/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.calculator2

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.round

/**
 * Computable real numbers, represented so that we can get exact decidable comparisons
 * for a number of interesting special cases, including rational computations.
 *
 * A real number is represented as the product of two numbers with different representations:
 * > A) A [BoundedRational] that can only represent a subset of the rationals, but supports
 * > exact computable comparisons.
 *
 * > B) A lazily evaluated "constructive real number" ([CR]) that provides operations to evaluate
 * > itself to any requested number of digits.
 *
 * Whenever possible, we choose (B) to be one of a small set of known constants about which we
 * know more.  For example, whenever we can, we represent rationals such that (B) is 1.
 * This scheme allows us to do some very limited symbolic computation on numbers when both
 * have the same (B) value, as well as in some other situations.  We try to maximize that
 * possibility.
 *
 * Arithmetic operations and operations that produce finite approximations may throw unchecked
 * exceptions produced by the underlying [CR] and [BoundedRational] packages, including
 * [CR.PrecisionOverflowException] and [CR.AbortedException].
 */
@Suppress("MemberVisibilityCanBePrivate")
class UnifiedReal private constructor(
        private val mRatFactor: BoundedRational,
        private val mCrFactor: CR) {

    /**
     * Return (*this* mod 2pi)/(pi/6) as a [BigInteger], or *null* if that isn't easily possible.
     */
    private val piTwelfths: BigInteger?
        get() {
            if (definitelyZero()) return BigInteger.ZERO
            if (mCrFactor === CR_PI) {
                val quotient = BoundedRational.asBigInteger(
                        BoundedRational.multiply(mRatFactor, BoundedRational.TWELVE)) ?: return null
                return quotient.mod(BIG_24)
            }
            return null
        }

/*
    init {
        if (mRatFactor == null) {
            throw ArithmeticException("Building UnifiedReal from null")
        }
    }// We don't normally traffic in null CRs, and hence don't test explicitly.
*/

    constructor(cr: CR) : this(BoundedRational.ONE, cr)

    constructor(rat: BoundedRational) : this(rat, CR_ONE)

    constructor(n: BigInteger) : this(BoundedRational(n))

    constructor(n: Long) : this(BoundedRational(n))

    /**
     * Given a constructive real [cr], try to determine whether [cr] is the logarithm of a small
     * integer.  If so, return exp([cr]) as a [BoundedRational].  Otherwise return *null*.
     * We make this determination by simple table lookup, so spurious *null* returns are
     * entirely possible, or even likely. We loop over `i` for range of valid inidces for the
     * array [sLogs], and if the `i`'th entry in [sLogs] points to the same object as our parameter
     * [cr] we return a [BoundedRational] constructed from `i` to the caller. If none of the
     * entries in [sLogs] matches [cr] we return *null*.
     *
     * @param cr The [CR] we are to look up in our table of logariths of small numbers.
     * @return A [BoundedRational] constructed from a small integer or *null*.
     */
    private fun getExp(cr: CR): BoundedRational? {
        for (i in sLogs.indices) {
            if (sLogs[i] === cr) {
                return BoundedRational(i.toLong())
            }
        }
        return null
    }

    /**
     * Is this number known to be rational?
     *
     * @return *true* if our [mCrFactor] field is [CR_ONE], or our [mRatFactor] is equal to 0.
     */
    fun definitelyRational(): Boolean {
        return mCrFactor === CR_ONE || mRatFactor.signum() == 0
    }

    /**
     * Is this number known to be irrational?
     * TODO: We could track the fact that something is irrational with an explicit flag, which
     * could cover many more cases.  Whether that matters in practice is TBD.
     *
     * @return *true* if our [definitelyRational] determines that our number is not definitely a
     * rational number and our [isNamed] method determines that our [mCrFactor] field is among the
     * well-known constructive reals we know about.
     */
    fun definitelyIrrational(): Boolean {
        return !definitelyRational() && isNamed(mCrFactor)
    }

    /**
     * Is this number known to be algebraic?
     *
     * @return *true* if our [definitelyAlgebraic] method determines that our [mCrFactor] field is
     * known to be algebraic (ie. either the constant [CR_ONE] or other known [CR] constant) or our
     * [mRatFactor] field is 0.
     */
    fun definitelyAlgebraic(): Boolean {
        return definitelyAlgebraic(mCrFactor) || mRatFactor.signum() == 0
    }

    /**
     * Is this number known to be transcendental?
     *
     * @return *true* if our [definitelyAlgebraic] method returns *false* (we are not an algebraic
     * number) and our [isNamed] method determines that our [mCrFactor] field is one of our named
     * well-known constructive reals.
     */
    @Suppress("unused")
    fun definitelyTranscendental(): Boolean {
        return !definitelyAlgebraic() && isNamed(mCrFactor)
    }

    /**
     * Convert to String reflecting raw representation. Debug or log messages only, not pretty.
     *
     * @return a string formed by concatenating the string value of our [mRatFactor] field followed
     * by the "*" multiply character followed by the string value of our [mCrFactor] field.
     */
    override fun toString(): String {
        @Suppress("ConvertToStringTemplate")
        return mRatFactor.toString() + "*" + mCrFactor.toString()
    }

    /**
     * Convert to readable String. Intended for user output. Produces exact expression when possible.
     * If our [mCrFactor] field is [CR_ONE] (we are a rational number) or our [mRatFactor] is 0 we
     * return the string returned by the `toNiceString` method of [mRatFactor] to the caller. If not
     * we initialize our `val name` to the [String] returned by our [crName] method for [mCrFactor].
     * If `name` is not *null* we initialize our `val bi` to the [BigInteger] returned by the
     * [BoundedRational.asBigInteger] method for [mRatFactor] then return:
     * - `name` if `bi` is [BigInteger.ONE]
     * - Theconcatenating by `name` if
     * `bi` is not *null*
     * - The [String] formed by concatenating "(" followed by the [String] returned by the
     * `toNiceString` method of [mRatFactor] followed by ")" followed by `name` if `bi` is
     * *null*.
     *
     * If `name` is *null* we return the [String] returned by the `toString` method of [mCrFactor]
     * if [mRatFactor] is [BoundedRational.ONE], otherwise we return the [String] returned by the
     * `toString` method the [CR] returned by our `crValue` method to the caller.
     *
     * @return readable [String] representation suitable for user output.
     */
    fun toNiceString(): String {
        if (mCrFactor === CR_ONE || mRatFactor.signum() == 0) {
            return mRatFactor.toNiceString()
        }
        val name = crName(mCrFactor)
        if (name != null) {
            val bi = BoundedRational.asBigInteger(mRatFactor)
            return if (bi != null) {
                if (bi == BigInteger.ONE) {
                    name
                } else mRatFactor.toNiceString() + name
            } else "(" + mRatFactor.toNiceString() + ")" + name
        }
        return if (mRatFactor == BoundedRational.ONE) {
            mCrFactor.toString()
        } else crValue().toString()
    }

    /**
     * Will toNiceString() produce an exact representation?
     *
     * @return *true* if our [crName] method determines that our [mCrFactor] field is a well-known
     * constructive real.
     */
    fun exactlyDisplayable(): Boolean {
        return crName(mCrFactor) != null
    }

    /**
     * Returns a truncated representation of the result. If exactlyTruncatable(), we round correctly
     * towards zero. Otherwise the resulting digit string may occasionally be rounded up instead.
     * Always includes a decimal point in the result. The result includes [n] digits to the right of
     * the decimal point. If our [mCrFactor] field is [CR_ONE] (we are a rational number) or our
     * [mRatFactor] is [BoundedRational.ZERO] we return the string returned by the `toStringTruncated`
     * method of [mRatFactor] for [n] digits of precision to the caller. Otherwise we initialize our
     * `val scaled` to the [CR] created by multiplying our value as a [CR] by the [CR] created from
     * the [BigInteger] of 10 to the [n]. We initialize our `var negative` to *false*, and declare
     * `var intScaled` to be a [BigInteger]. If our method [exactlyTruncatable] returns *true* (to
     * indicate that we can compute correctly truncated approximations) we wet `intScaled` to the
     * [BigInteger] returned by the `approxGet` method of `scaled`. If the `signum` method indicated
     * thatn `intScaled` is negative we set `negative` to *true* and negate `intScaled`. If the
     * `compareTo` method of the [CR] of `intScaled` determines that `intScaled` is greater than
     * the absolute value of `scaled` we subtract [BigInteger.ONE] from `intScaled`. We then call
     * the `check` method of [CR] to make sure `intScaled` is less than `scaled`. If on the other
     * hand [exactlyTruncatable] returns *false* we set `intScaled` to the [BigInteger] returned by
     * the `approxGet` method for `scaled` to the precision of minus EXTRA_PREC. If `intScaled` is
     * negative we set `negative` to *true* and negate `intScaled`. We then shift `intScaled` right
     * by EXTRA_PREC.
     *
     * Next we initialize our `var digits` to the string value of `intScaled`, and `var len` to the
     * length of `digits`. If `len` is less than [n] plus 1 we add [n] plus 1 minus `len` "0" digits
     * to the beginning of `digits` and set `len` to [n] plus 1. Finally we return a [String] formed
     * by concatenating a "-" character if `negative` is *true* followed by the substring of `digits`
     * from 0 to `len` minus [n], followed by a "." decimal point followed by the substring of
     * `digits` from `len` minus [n] to its end.
     *
     * @param n result precision, >= 0
     * @return string representation of our value with [n] digits to the right of the decimal point.
     */
    fun toStringTruncated(n: Int): String {
        if (mCrFactor === CR_ONE || mRatFactor === BoundedRational.ZERO) {
            return mRatFactor.toStringTruncated(n)
        }
        val scaled = CR.valueOf(BigInteger.TEN.pow(n)).multiply(crValue())
        var negative = false
        var intScaled: BigInteger
        if (exactlyTruncatable()) {
            intScaled = scaled.approxGet(0)
            if (intScaled.signum() < 0) {
                negative = true
                intScaled = intScaled.negate()
            }
            @Suppress("ReplaceCallWithBinaryOperator")
            if (CR.valueOf(intScaled).compareTo(scaled.abs()) > 0) {
                intScaled = intScaled.subtract(BigInteger.ONE)
            }
            @Suppress("ReplaceCallWithBinaryOperator")
            check(CR.valueOf(intScaled).compareTo(scaled.abs()) < 0)
        } else {
            // Approximate case.  Exact comparisons are impossible.
            intScaled = scaled.approxGet(-EXTRA_PREC)
            if (intScaled.signum() < 0) {
                negative = true
                intScaled = intScaled.negate()
            }
            intScaled = intScaled.shiftRight(EXTRA_PREC)
        }
        var digits = intScaled.toString()
        var len = digits.length
        if (len < n + 1) {
            digits = StringUtils.repeat('0', n + 1 - len) + digits
            len = n + 1
        }
        return ((if (negative) "-" else "") + digits.substring(0, len - n) + "."
                + digits.substring(len - n))
    }

    /**
     * Can we compute correctly truncated approximations of this number? We return *true* is our
     * [mCrFactor] field is [CR_ONE] (we are rational) or our [mRatFactor] field is ZERO, or if
     * our [definitelyIrrational] method returns *true* (we are one of the well known irrational
     * numbers).
     *
     * @return *true* if we can compute correctly truncated approximations of this number.
     */
    fun exactlyTruncatable(): Boolean {
        // If the value is known rational, we can do exact comparisons.
        // If the value is known irrational, then we can safely compare to rational approximations;
        // equality is impossible; hence the comparison must converge.
        // The only problem cases are the ones in which we don't know.
        return mCrFactor === CR_ONE || mRatFactor === BoundedRational.ZERO || definitelyIrrational()
    }

    /**
     * Return a double approximation. Rational arguments are currently rounded to nearest, with ties
     * away from zero. If our [mCrFactor] field is [CR_ONE] we return the [Double] value returned by
     * the [doubleValue] method of our [mRatFactor] field, otherwise we return the [Double] returned
     * by the [toDouble] method of the [CR] computed by our [crValue] method (it returns our
     * [mRatFactor] field multiplied by our [mCrFactor] field).
     *
     * @return Our value converted to a [Double].
     */
    @Suppress("unused")
    fun doubleValue(): Double {
        return if (mCrFactor === CR_ONE) {
            mRatFactor.doubleValue() // Hopefully correctly rounded
        } else {
            crValue().toDouble() // Approximately correctly rounded
        }
    }

    /**
     * Computes our value as a [CR] by multiplying our [mRatFactor] field by our [mCrFactor] field.
     *
     * @return the [CR] created by multiplying our [mRatFactor] field by our [mCrFactor] field.
     */
    fun crValue(): CR {
        return mRatFactor.crValue().multiply(mCrFactor)
    }

    /**
     * Are *this* and [u] exactly comparable? There are four conditions where we declare [u] to be
     * exactly comparable to *this*:
     * - When our [mCrFactor] field points to the same [CR] as the [mCrFactor] field of [u] and
     * [mCrFactor] is either a well known [CR] or is within DEFAULT_COMPARE_TOLERANCE (-1000 bits)
     * tolerance of 0.000 - we return *true*.
     * - When our [mRatFactor] field is 0, and the [mRatFactor] field of [u] is 0 - we return *true*.
     * - When our [definitelyIndependent] method determines that our [mCrFactor] field and the
     * [mCrFactor] field of [u] differ by something other than a a rational factor - we return *true*.
     * - When our value as a [CR] calculated by our [crValue] method is not equal to the value of [u]
     * as a [CR] within DEFAULT_COMPARE_TOLERANCE (-1000 bits) tolerance - we return *true*.
     *
     * Otherwise we return *false*.
     *
     * @param u The other [UnifiedReal] to be compared against.
     * @return *true* if it is possible to compare *this* [UnifiedReal] to our parameter [u].
     */
    fun isComparable(u: UnifiedReal): Boolean {
        // We check for ONE only to speed up the common case.
        // The use of a tolerance here means we can spuriously return false, not true.
        return (mCrFactor === u.mCrFactor && (isNamed(mCrFactor) || mCrFactor.signum(DEFAULT_COMPARE_TOLERANCE) != 0)
                || mRatFactor.signum() == 0 && u.mRatFactor.signum() == 0
                || definitelyIndependent(mCrFactor, u.mCrFactor)
                || crValue().compareTo(u.crValue(), DEFAULT_COMPARE_TOLERANCE) != 0)
    }

    /**
     * Return +1 if *this* is greater than [u], -1 if *this* is less than [u], or 0 of the two are
     * known to be equal. May diverge if the two are equal and our [isComparable] method returns
     * *false* for [u]. If our [definitelyZero] method returns *true* indicating that *this* is 0
     * and the [definitelyZero] method of [u] also returns *true* we return 0 to the caller. If our
     * [mCrFactor] field points to the same [CR] as the [mCrFactor] field of [u] we initialize our
     * `val signum` to the value returned by the `signum` method of [mCrFactor] then return `signum`
     * times the value returned by the `compareTo` method of our field [mRatFactor] when it compares
     * itself to the [mRatFactor] field of [u]. Otherwise we return the value returned by the
     * `compareTo` method of our value as a [CR] that is calculated by our [crValue] when it compares
     * itself to the [CR] value of [u] that its `crValue` method calculates.
     *
     * @param u The other [UnifiedReal] to be compared to.
     * @return +1 if *this* is greater than [u], -1 if *this* is less than [u], or 0 of the two are
     * known to be equal.
     */
    operator fun compareTo(u: UnifiedReal): Int {
        if (definitelyZero() && u.definitelyZero()) return 0
        if (mCrFactor === u.mCrFactor) {
            val signum = mCrFactor.signum()  // Can diverge if mCrFactor == 0.
            return signum * mRatFactor.compareTo(u.mRatFactor)
        }
        return crValue().compareTo(u.crValue())  // Can also diverge.
    }

    /**
     * Return +1 if this is greater than r, -1 if this is less than r, or possibly 0 of the two are
     * within 2^a of each other. If our [isComparable] returns *true* to indicate that it is possible
     * to compare *this* to [u] we return the value returned by our `compareTo(UnifiedReal)` method
     * when given [u]. Otherwise we return the value returned by the `compareTo` method of our value
     * as a [CR] that our [crValue] method calculates when that method compares *this* to the value
     * as a [CR] of [u] with a as the tolerance in bits.
     *
     * @param u The other [UnifiedReal]
     * @param a Absolute tolerance in bits
     * @return +1 if this is greater than r, -1 if this is less than r, or possibly 0 of the two are
     * within 2^a of each other
     */
    fun compareTo(u: UnifiedReal, a: Int): Int {
        return if (isComparable(u)) {
            compareTo(u)
        } else {
            crValue().compareTo(u.crValue(), a)
        }
    }

    /**
     * Return compareTo(ZERO, a).
     *
     * @param a Absolute tolerance in bits
     * @return the value returned by our [compareTo] method when it compares [ZERO] to *this* to a
     * precision of [a] bits.
     */
    fun signum(a: Int): Int {
        return compareTo(ZERO, a)
    }

    /**
     * Return compareTo(ZERO). May diverge for ZERO argument if !isComparable(ZERO).
     *
     * @return the value returned by our [compareTo] method when it compares [ZERO] to *this*
     */
    fun signum(): Int {
        return compareTo(ZERO)
    }

    /**
     * Equality comparison. May erroneously return *true* if values differ by less than 2^a, and
     * !isComparable(u). If our [isComparable] method determines that [u] is comparable to *this*,
     * we call our [definitelyIndependent] returns *true* when comparing the [mCrFactor] field of
     * this and the [mCrFactor] of [u] and our [mRatFactor] field is not equal to 0 or the same
     * field of [u] is not equal to 0 we return *false* without doing any further work, otherwise
     * we call our [compareTo] method to compare *this* to [u] and return *true* if it returns 0.
     * If our [isComparable] method returns *false* on the other hand we return *true* if the
     * `compareTo` method of our value as a [CR] as calculated by our [crValue] method determines
     * that the value as a [CR] of [u] is within the tolerance of [a] of us.
     *
     * @param u The other [UnifiedReal]
     * @param a Absolute tolerance in bits
     * @return *true* if [u] is approximately equal to *this* to a precision of [a] bits.
     */
    fun approxEquals(u: UnifiedReal, a: Int): Boolean {
        return if (isComparable(u)) {
            if (definitelyIndependent(mCrFactor, u.mCrFactor) && (mRatFactor.signum() != 0
                            || u.mRatFactor.signum() != 0)) {
                // No need to actually evaluate, though we don't know which is larger.
                false
            } else {
                compareTo(u) == 0
            }
        } else crValue().compareTo(u.crValue(), a) == 0
    }

    /**
     * Returns *true* if values are definitely known to be equal, *false* in all other cases.
     * This does not satisfy the contract for Object.equals(). We return *true* if our [isComparable]
     * method determines that [u] is comparable to *this* and our [compareTo] method returns 0 to
     * indicate that [u] is equal to *this*.
     *
     * @param u The other [UnifiedReal]
     * @return *true* if [u] is definitely known to be equal to *this*.
     */
    fun definitelyEquals(u: UnifiedReal): Boolean {
        return isComparable(u) && compareTo(u) == 0
    }

    /**
     * Returns a hash code value for the object. We just return the useless value 0.
     *
     * @return we always return 0.
     */
    override fun hashCode(): Int {
        // Better useless than wrong. Probably.
        return 0
    }

    /**
     * Indicates whether some other object is "equal to" this one. Implementations must fulfil the
     * following requirements:
     * * Reflexive: for any non-null value `x`, `x.equals(x)` should return true.
     * * Symmetric: for any non-null values `x` and `y`, `x.equals(y)` should return true if and
     * only if `y.equals(x)` returns true.
     * * Transitive:  for any non-null values `x`, `y`, and `z`, if `x.equals(y)` returns true
     * and `y.equals(z)` returns true, then `x.equals(z)` should return true.
     * * Consistent:  for any non-null values `x` and `y`, multiple invocations of `x.equals(y)`
     * consistently return true or consistently return false, provided no information used in
     * `equals` comparisons on the objects is modified.
     * * Never equal to null: for any non-null value `x`, `x.equals(null)` should return false.
     *
     * @param other The other kotlin object (*Any*) we are comparing *this* to.
     * @return we always return *false* if *this* is not being compared to another instance of
     * [UnifiedReal] and is *null*, if we are being compared to another instance we throw an
     * [AssertionError] because one "Can't compare UnifiedReals for exact equality".
     */
    override fun equals(other: Any?): Boolean {

        if (other == null || other !is UnifiedReal) {
            return false
        }
        // This is almost certainly a programming error. Don't even try.
        throw AssertionError("Can't compare UnifiedReals for exact equality")
    }

    /**
     * Returns *true* if values are definitely known not to be equal, *false* in all other cases.
     * Performs no approximate evaluation. We set our `val isNamed` to *true* if our [isNamed]
     * method determines that our [mCrFactor] field points to one of the well known [CR] constants,
     * and our `val uIsNamed` to *true* if our [isNamed] method determines that the `mCrFactor`
     * field of [u] points to one of the well known [CR] constants. If both `isNamed` and `uIsNamed`
     * are *true* we branch on the whether our [definitelyIndependent] method determines that our
     * [mCrFactor] field and the `mCrFactor` field of [u] are definitely independent, returning
     * *true* if either our [mRatFactor] field or the `mRatFactor` field of [u] are not equal to 0.
     * If they are not definitely independent and our [mCrFactor] field points to the same [CR] as
     * the `mCrFactor` field of [u] we return *true* if our [mRatFactor] field is not equal to the
     * `mRatFactor` field of [u]. Otherwise we return *true* if our [mRatFactor] field is not equal
     * to the `mRatFactor` field of [u]. On the other hand if either `isNamed` or `uIsNamed` was
     * *false* we first check if our [mRatFactor] is 0 and if so return *true* if `uIsNamed` is
     * *true* and the `mRatFactor` field of [u] is not equal to 0. If our [mRatFactor] field is not
     * 0 we return *true* if the `mRatFactor` field of [u] is 0 `isNamed` is *true* and our
     * [mRatFactor] field is not 0. Otherwise we return *false*.
     *
     * @param u the other [UnifiedReal] we are comparing *this* to.
     * @return *true* it [u] is definitely not equal to *this*.
     */
    @Suppress("unused")
    fun definitelyNotEquals(u: UnifiedReal): Boolean {
        val isNamed = isNamed(mCrFactor)
        val uIsNamed = isNamed(u.mCrFactor)
        if (isNamed && uIsNamed) {
            if (definitelyIndependent(mCrFactor, u.mCrFactor)) {
                return mRatFactor.signum() != 0 || u.mRatFactor.signum() != 0
            } else if (mCrFactor === u.mCrFactor) {
                return mRatFactor != u.mRatFactor
            }
            return mRatFactor != u.mRatFactor
        }
        if (mRatFactor.signum() == 0) {
            return uIsNamed && u.mRatFactor.signum() != 0
        }
        return if (u.mRatFactor.signum() == 0) {
            isNamed && mRatFactor.signum() != 0
        } else false
    }

    // And some slightly faster convenience functions for special cases:

    /**
     * Returns *true* if our [mRatFactor] field is 0 (as determined by the `signum` method of that
     * [BoundedRational].
     *
     * @return *true* if our [mRatFactor] field is 0.
     */
    fun definitelyZero(): Boolean {
        return mRatFactor.signum() == 0
    }

    /**
     * Can this number be determined to be definitely nonzero without performing approximate
     * evaluation?
     *
     * @return *true* if our [mCrFactor] field points to a well known [CR] and the `signum` method
     * of our [mRatFactor] field determines that it is not 0.
     */
    @Suppress("unused")
    fun definitelyNonZero(): Boolean {
        return isNamed(mCrFactor) && mRatFactor.signum() != 0
    }

    /**
     * Is this number 1.00000...?
     *
     * @return *true* is our [mCrFactor] field points to the constant [CR_ONE] and our [mRatFactor]
     * field is equal to the constant [BoundedRational.ONE].
     */
    @Suppress("unused")
    fun definitelyOne(): Boolean {
        return mCrFactor === CR_ONE && mRatFactor == BoundedRational.ONE
    }

    /**
     * Return equivalent [BoundedRational], if known to exist, null otherwise.
     *
     * @return our [mRatFactor] field iff our [mCrFactor] field points to the constant [CR_ONE] and
     * the `signum` method indicates that our [mRatFactor] field is 0, otherwise we return *null*.
     */
    fun boundedRationalValue(): BoundedRational? {
        return if (mCrFactor === CR_ONE || mRatFactor.signum() == 0) {
            mRatFactor
        } else null
    }

    /**
     * Returns equivalent [BigInteger] result if it exists, null if not. We initialize our `val r`
     * to the [BoundedRational] that our [boundedRationalValue] method calculates for *this* (this
     * is *null* unless our [mCrFactor] field points to the constant [CR_ONE] or our [mRatFactor]
     * field is 0). Then we return the [BigInteger] that the [BoundedRational.asBigInteger] method
     * calculates from `r`.
     *
     * @return our value as a [BigInteger] or *null*.
     */
    fun bigIntegerValue(): BigInteger? {
        val r = boundedRationalValue()
        return BoundedRational.asBigInteger(r)
    }

    /**
     * Adds its argument and *this* then returns the result. If our [mCrFactor] field points to the
     * same [CR] as the [mCrFactor] field of [u] we initialize our `val nRatFactor` to the
     * [BoundedRational] that the [BoundedRational.add] method returns when it adds our [mRatFactor]
     * field to the [mRatFactor] field of [u]. If `nRatFactor` is not equal to *null* we return a
     * [UnifiedReal] constructed from `nRatFactor` and our [mCrFactor] field. If our [definitelyZero]
     * method returns *true* indicating that *this* is definitely 0 we just return [u]. If the
     * [definitelyZero] method of [u] returns *true* indicating that [u] is 0 we return *this*,
     * otherwise we return a [UnifiedReal] constructed from our value as a [CR] added to the value
     * of [u] as a [CR].
     *
     * @param u The [UnifiedReal] we are to add with *this*
     * @return The [UnifiedReal] that result from adding our parameter [u] and *this*
     */
    fun add(u: UnifiedReal): UnifiedReal {
        if (mCrFactor === u.mCrFactor) {
            val nRatFactor = BoundedRational.add(mRatFactor, u.mRatFactor)
            if (nRatFactor != null) {
                return UnifiedReal(nRatFactor, mCrFactor)
            }
        }
        if (definitelyZero()) {
            // Avoid creating new mCrFactor, even if they don't currently match.
            return u
        }
        return if (u.definitelyZero()) {
            this
        } else UnifiedReal(crValue().add(u.crValue()))
    }

    /**
     * Returns the negative of *this*, which is a [UnifiedReal] constructed from the negation of our
     * [mRatFactor] and our current [mCrFactor] field.
     *
     * @return the [UnifiedReal] that results from negating *this*
     */
    fun negate(): UnifiedReal {
        return UnifiedReal(BoundedRational.negate(mRatFactor), mCrFactor)
    }

    /**
     * Subtracts its argument from *this* then returns the result. We just return the [UnifiedReal]
     * that our [add] method returns after adding *this* with the negation of [u].
     *
     * @param u the [UnifiedReal] we are to subtract from *this*
     * @return the [UnifiedReal] that results from subtracting [u] from *this*.
     */
    fun subtract(u: UnifiedReal): UnifiedReal {
        return add(u.negate())
    }

    /**
     * Multiplies its argument and *this* then returns the result. If our [mCrFactor] field points
     * to the constant [CR_ONE] we initialize our `val nRatFactor` with the [BoundedRational] that
     * results when the [BoundedRational.multiply] method multiplies our [mRatFactor] field and
     * the [mRatFactor] field of our parameter [u]. If `nRatFactor` is not *null* we return a
     * [UnifiedReal] constructed from `nRatFactor` and the [mCrFactor] field of [u]. If the
     * [mCrFactor] field of [u] points to the constant [CR_ONE] we initialize our `val nRatFactor`
     * with the [BoundedRational] that results when the [BoundedRational.multiply] method multiplies
     * our [mRatFactor] field and the [mRatFactor] field of our parameter [u]. If `nRatFactor` is
     * not *null* we return a [UnifiedReal] constructed from `nRatFactor` and our [mCrFactor] field.
     * If our [definitelyZero] method indicates that *this* is definitely 0, or the [definitelyZero]
     * method of [u] indicates that [u] is definitely 0 we return the constant [ZERO]. If our
     * [mCrFactor] field points to the same [CR] that the [mCrFactor] field or [u] points to we
     * initialize our `val square` to the small integer [BoundedRational] that our [getSquare]
     * method determines we are the square root of (or *null* if we are not the square root of a
     * small integer). If `square` is not *null* we initialize our `val nRatFactor` to the
     * [BoundedRational] that results when the [BoundedRational.multiply] method multiplies the
     * result of multiplying `square` by our [mRatFactor] field by the [mRatFactor] field of [u].
     * If `nRatFactor` is not *null* we return a [UnifiedReal] constructed from `nRatFactor`.
     * Otherwise we initialize our `val nRatFactor` to the [BoundedRational] that results when the
     * [BoundedRational.multiply] method multiplies our [mRatFactor] field by the [mRatFactor] field
     * of [u]. If `nRatFactor` is not *null* we return a [UnifiedReal] constructed from `nRatFactor`
     * and the result of multiplying our [mCrFactor] field by the [mCrFactor] field of [u], otherwise
     * we return a [UnifiedReal] constructed from the result of multiplying our value as a [CR] by
     * the value of [u] as a [CR].
     *
     * @param u the [UnifiedReal] we are to multiply *this* by.
     * @return the [UnifiedReal] that results from multiplying [u] and *this*.
     */
    fun multiply(u: UnifiedReal): UnifiedReal {
        // Preserve a preexisting mCrFactor when we can.
        if (mCrFactor === CR_ONE) {
            val nRatFactor = BoundedRational.multiply(mRatFactor, u.mRatFactor)
            if (nRatFactor != null) {
                return UnifiedReal(nRatFactor, u.mCrFactor)
            }
        }
        if (u.mCrFactor === CR_ONE) {
            val nRatFactor = BoundedRational.multiply(mRatFactor, u.mRatFactor)
            if (nRatFactor != null) {
                return UnifiedReal(nRatFactor, mCrFactor)
            }
        }
        if (definitelyZero() || u.definitelyZero()) {
            return ZERO
        }
        if (mCrFactor === u.mCrFactor) {
            val square = getSquare(mCrFactor)
            if (square != null) {

                val nRatFactor = BoundedRational.multiply(
                        BoundedRational.multiply(square, mRatFactor)!!, u.mRatFactor)
                if (nRatFactor != null) {
                    return UnifiedReal(nRatFactor)
                }
            }
        }
        // Probably a bit cheaper to multiply component-wise.
        val nRatFactor = BoundedRational.multiply(mRatFactor, u.mRatFactor)
        return if (nRatFactor != null) {
            UnifiedReal(nRatFactor, mCrFactor.multiply(u.mCrFactor))
        } else UnifiedReal(crValue().multiply(u.crValue()))
    }

    /**
     * The [Exception] we throw when our [divide] method is asked to divide by 0, or our [inverse]
     * method is asked to return the reciprocal of 0.
     */
    class ZeroDivisionException : ArithmeticException("Division by zero")

    /**
     * Return the reciprocal. If our [definitelyZero] method returns *true* indicating that *this*
     * is definitely 0 we throw a new instance of [ZeroDivisionException]. Otherwise we initialize
     * our `val square` with the small integer [BoundedRational] that our [getSquare] method finds
     * that our [mCrFactor] field is the square root of. If `square` is not *null* we can calculate
     * the reciprocal to be the square root of that small integer divided by that integer so we
     * initialize our `val nRatFactor` to be the [BoundedRational] returned by the method
     * [BoundedRational.inverse] when inverting the product of our [mRatFactor] field and `square`,
     * and if `nRatFactor` is not *null* we return a [UnifiedReal] constructed from `nRatFactor`
     * and our [mCrFactor] field. If `square` is *null* we return a [UnifiedReal] constructed from
     * the inverse of our [mRatFactor] field and the inverse of our [mCrFactor] field.
     *
     * @return a [UnifiedReal] which is the reciprocal of *this*.
     */
    fun inverse(): UnifiedReal {
        if (definitelyZero()) {
            throw ZeroDivisionException()
        }
        val square = getSquare(mCrFactor)
        if (square != null) {
            // 1/sqrt(n) = sqrt(n)/n
            val nRatFactor = BoundedRational.inverse(
                    BoundedRational.multiply(mRatFactor, square))
            if (nRatFactor != null) {
                return UnifiedReal(nRatFactor, mCrFactor)
            }
        }
        return UnifiedReal(BoundedRational.inverse(mRatFactor)!!, mCrFactor.inverse())
    }

    /**
     * Returns a [UnifiedReal] which is the result of dividing *this* by our parameter [u]. If our
     * [mCrFactor] field points to the same [CR] that the [mCrFactor] field of [u] points to we
     * check if [u] is 0 and throw a new instance of [ZeroDivisionException] if it is, and if it is
     * not we initialize our `val nRatFactor` to the [BoundedRational] that the method
     * [BoundedRational.divide] returns when it divides our [mRatFactor] field by the [mRatFactor]
     * field of [u]. If `nRatFactor` is not *null* we return a [UnifiedReal] constructed from
     * `nRatFactor` and the constant [CR_ONE]. If on the other hand [mCrFactor] is not equal to
     * the [mCrFactor] field of [u] we return the [UnifiedReal] that our [multiply] method returns
     * when it multiplies *this* by the inverse of [u].
     *
     * @param u The [UnifiedReal] we are to divide *this* by
     * @return A [UnifiedReal] which is the result of dividing *this* by our parameter [u].
     */
    fun divide(u: UnifiedReal): UnifiedReal {
        if (mCrFactor === u.mCrFactor) {
            if (u.definitelyZero()) {
                throw ZeroDivisionException()
            }
            val nRatFactor = BoundedRational.divide(mRatFactor, u.mRatFactor)
            if (nRatFactor != null) {
                return UnifiedReal(nRatFactor, CR_ONE)
            }
        }
        return multiply(u.inverse())
    }

    /**
     * Return the square root. This may fail to return a known rational value, even when the result
     * is rational. If our [definitelyZero] determines that we are definitely equal to 0 we return
     * the constant [ZERO] to the caller. If our [mCrFactor] field points to the same [CR] as the
     * constant [CR_ONE] we declare `var ratSqrt` to be a [BoundedRational] then loop over the [Int]
     * `divisor` from 1 until the size of our [sSqrts] array of [CR] of the square root of small
     * integers setting `ratSqrt` to the [BoundedRational] returned by the [BoundedRational.sqrt]
     * method when it takes the square root of our [mRatFactor] field divided by the [BoundedRational]
     * constructed from `divisor`. If `ratSqrt` is not *null* we return the [UnifiedReal] constructed
     * from `ratSqrt` and the `divisor` entry in the [sSqrts] array, and if it is *null* we loop
     * around to try the next entry in [sSqrts].
     *
     * If none of the entries in [sSqrts] worked we return a [UnifiedReal] constructed from the
     * square root of our value as a [CR].
     *
     * @return a [UnifiedReal] which is the square root of *this*
     */
    fun sqrt(): UnifiedReal {
        if (definitelyZero()) {
            return ZERO
        }
        if (mCrFactor === CR_ONE) {
            var ratSqrt: BoundedRational?
            // Check for all arguments of the form <perfect rational square> * small_int,
            // where small_int has a known sqrt.  This includes the small_int = 1 case.
            for (divisor in 1 until sSqrts.size) {
                if (sSqrts[divisor] != null) {
                    ratSqrt = BoundedRational.sqrt(
                            BoundedRational.divide(mRatFactor, BoundedRational(divisor.toLong())))
                    if (ratSqrt != null) {
                        return UnifiedReal(ratSqrt, sSqrts[divisor]!!)
                    }
                }
            }
        }
        return UnifiedReal(crValue().sqrt())
    }

    /**
     * Returns the trigonometric sine of *this*. First we initialize our `val piTwelfths` to the
     * [BigInteger] of (*this* mod 2pi)/(pi/6) iff our [mCrFactor] points to the constant [CR_PI]
     * (ie. we are a rational multiple of pi). If `piTwelfths` is not *null* we initialize our
     * `val result` to the [UnifiedReal] that our [sinPiTwelfths] method finds when it looks up
     * `piTwelfths` in the list of known results of the sine of integer multiples of pi/12, and if
     * `result` is not *null* we return `result`. Otherwise we return a [UnifiedReal] constructed
     * from the sine of our value as a [CR].
     *
     * @return The trigonometric sine of *this*
     */
    fun sin(): UnifiedReal {
        val piTwelfths = piTwelfths
        if (piTwelfths != null) {
            val result = sinPiTwelfths(piTwelfths.toInt())
            if (result != null) {
                return result
            }
        }
        return UnifiedReal(crValue().sin())
    }

    /**
     *  Returns the trigonometric cosine of *this*. First we initialize our `val piTwelfths` to the
     * [BigInteger] of (*this* mod 2pi)/(pi/6) iff our [mCrFactor] points to the constant [CR_PI]
     * (ie. we are a rational multiple of pi). If `piTwelfths` is not *null* we initialize our
     * `val result` to the [UnifiedReal] that our [cosPiTwelfths] method finds when it looks up
     * `piTwelfths` in the list of known results of the cosine of integer multiples of pi/12, and if
     * `result` is not *null* we return `result`. Otherwise we return a [UnifiedReal] constructed
     * from the cosine of our value as a [CR].
     *
     * @return The trigonometric cosine of *this*
     */
    fun cos(): UnifiedReal {
        val piTwelfths = piTwelfths
        if (piTwelfths != null) {
            val result = cosPiTwelfths(piTwelfths.toInt())
            if (result != null) {
                return result
            }
        }
        return UnifiedReal(crValue().cos())
    }

    /**
     * Returns the trigonometric tangent of *this*. First we initialize our `val piTwelfths` to the
     * [BigInteger] of (*this* mod 2pi)/(pi/6) iff our [mCrFactor] points to the constant [CR_PI]
     * (ie. we are a rational multiple of pi). If `piTwelfths` is not *null* we initialize our
     * `val i` to the [Int] value of `piTwelfths`. If `i` is equal to 6 or equal to 18 we throw an
     * [ArithmeticException] ("Tangent undefined"). Otherwise we initialize our `val top` to the
     * [UnifiedReal] sine value that our [sinPiTwelfths] method finds for `i` and our `val bottom`
     * to the [UnifiedReal] cosine value that our [cosPiTwelfths] method finds for `i`. If `top`
     * is not *null* and `bottom` is not *null* we return `top` divided by `bottom`. If we are not
     * a rational multiple of pi we return the [sin] of *this* divided by the [cos] of *this*.
     *
     * @return The trigonometric tangent of *this*
     */
    @Suppress("unused")
    fun tan(): UnifiedReal {
        val piTwelfths = piTwelfths
        if (piTwelfths != null) {
            val i = piTwelfths.toInt()
            if (i == 6 || i == 18) {
                throw ArithmeticException("Tangent undefined")
            }
            val top = sinPiTwelfths(i)
            val bottom = cosPiTwelfths(i)
            if (top != null && bottom != null) {
                return top.divide(bottom)
            }
        }
        return sin().divide(cos())
    }

    /**
     * Throw an exception if *this* is definitely out of bounds for arcsine or arccosine. If our
     * [isComparable] method determines that we can be exactly compared to the [UnifiedReal] constant
     * [ONE] we check whether *this* is greater than [ONE] or less than [ONE] and if so we throw an
     * [ArithmeticException] "inverse trig argument out of range".
     */
    private fun checkAsinDomain() {
        if (isComparable(ONE) && (compareTo(ONE) > 0 || compareTo(MINUS_ONE) < 0)) {
            throw ArithmeticException("inverse trig argument out of range")
        }
    }

    /**
     * Return arcsine of *this*, assuming *this* is not an integral multiple of a half. If our
     * [compareTo] method determines we are less than 0 to a tolerance of 10 bits we return the
     * negation of the result returned by this method for the negative of *this*. If we are found
     * to be definitely equal to the constant [HALF_SQRT2] by our [definitelyEquals] method we
     * return a [UnifiedReal] constructed from the constant [BoundedRational.QUARTER] and the
     * constant [CR_PI] (one fourth of pi). If we are found to be definitely equal to the constant
     * [HALF_SQRT3] by our [definitelyEquals] method we return a [UnifiedReal] constructed from the
     * constant [BoundedRational.THIRD] and the constant [CR_PI] (one third of pi). Otherwise we
     * return a [UnifiedReal] constructed from the arcsine of our value as a [CR].
     *
     * @return a [UnifiedReal] which is the arcsine of *this*
     */
    fun asinNonHalves(): UnifiedReal {
        if (compareTo(ZERO, -10) < 0) {
            return negate().asinNonHalves().negate()
        }
        if (definitelyEquals(HALF_SQRT2)) {
            return UnifiedReal(BoundedRational.QUARTER, CR_PI)
        }
        return if (definitelyEquals(HALF_SQRT3)) {
            UnifiedReal(BoundedRational.THIRD, CR_PI)
        } else UnifiedReal(crValue().asin())
    }

    /**
     * Return arcsine of *this*. First we call our method [checkAsinDomain] to make sure *this* is
     * with the domain of the arcine function (which throws an [ArithmeticException] "inverse trig
     * argument out of range" if *this* is greater than 1 or less than -1). Then we initialize our
     * `val halves` to the [BigInteger] that results from multiplying *this* by the constant [TWO]
     * and then trying to convert *this* to a [BigInteger]. If `halves` is not *null* we return the
     * [UnifiedReal] that our [asinHalves] method returns for the [Int] value of `halves`. If our
     * [mCrFactor] field points to the constant [CR.ONE] or does not point to the constant [CR_SQRT2]
     * or does not point to the constant [CR_SQRT3] we return the [UnifiedReal] returned by our
     * [asinNonHalves] method, otherwise we return a [UnifiedReal] constructed from the arcsine of
     * our value as a [CR].
     *
     * @return a [UnifiedReal] which is the arcsine of *this*
     */
    fun asin(): UnifiedReal {
        checkAsinDomain()
        val halves = multiply(TWO).bigIntegerValue()
        if (halves != null) {
            return asinHalves(halves.toInt())
        }
        return if (mCrFactor === CR.ONE || mCrFactor !== CR_SQRT2 || mCrFactor !== CR_SQRT3) {
            asinNonHalves()
        } else UnifiedReal(crValue().asin())
    }

    /**
     * Return the arccosine of *this*. We just return the arcsine of *this* that our [asin] method
     * calculates subtracted from the constant [PI_OVER_2].
     *
     * @return a [UnifiedReal] which is the arccosine of *this*.
     */
    fun acos(): UnifiedReal {
        return PI_OVER_2.subtract(asin())
    }

    /**
     * Return the arctangent of *this*. If our [compareTo] method determines we are less than 0 to
     * a tolerance of 10 bits we return the negation of the result returned by this method for the
     * negative of *this*. Otherwise we intialize our `val asBI` to the [BigInteger] value of *this*
     * if it exists. If `asBI` is not *null* and it is less than or equal to the constant
     * [BigInteger.ONE] we return the constant [ZERO] if the value of `asBI` as an [Int] is 0, the
     * constant [PI_OVER_4] if it is 1, or else throw an [AssertionError] "Impossible r_int". If
     * *this* is not 0 or 1 we check if *this* is definitely equal to the constant [THIRD_SQRT3] and
     * if so return the constant [PI_OVER_6]. If *this* is definitely equal to the constant [SQRT3]
     * we return the constant [PI_OVER_3]. Otherwise we return a [UnifiedReal] constructed from the
     * [CR] that the `execute` method of the [AtanUnaryCRFunction] returns for our value as a [CR].
     *
     * @return a [UnifiedReal] which is the arctangent of *this*.
     */
    fun atan(): UnifiedReal {
        if (compareTo(ZERO, -10) < 0) {
            return negate().atan().negate()
        }
        val asBI = bigIntegerValue()
        @Suppress("ReplaceCallWithBinaryOperator")
        if (asBI != null && asBI.compareTo(BigInteger.ONE) <= 0) {
            // These seem to be all rational cases:
            return when (asBI.toInt()) {
                0 -> ZERO
                1 -> PI_OVER_4
                else -> throw AssertionError("Impossible r_int")
            }
        }
        if (definitelyEquals(THIRD_SQRT3)) {
            return PI_OVER_6
        }
        return if (definitelyEquals(SQRT3)) {
            PI_OVER_3
        } else UnifiedReal(UnaryCRFunction.atanFunction.execute(crValue()))
    }

    /**
     * Compute an integral power of our value as a constructive real, using the `exp()` function
     * when we safely can, and using [recursivePow] when we can't. [exp] is known to be nonzero.
     * First we intialize our `val sign` with the value returned by our [signum] method for the bit
     * tolerance of [DEFAULT_COMPARE_TOLERANCE] (which is -1 if *this* is less than 0, +1 if *this*
     * is greater than 0, and 0 if *this* is 0 within [DEFAULT_COMPARE_TOLERANCE] bits of tolerance).
     * We then branch on the value of `sign`:
     *  - Greater than 0, we return a [UnifiedReal] constructed from the [CR] that results from
     *  applying the exponential function `exp` to the natural log of our value as a [CR] multiplied
     *  by the value of [exp] as a [CR].
     *  - Less than 0, we initialize our `var result` to the [CR] created by taking our value as a
     *  [CR], negating it, taking the natural log of that value and multiplying it by the value of
     *  [exp] as a [CR] then applying the exponential function `exp` to that result. If [exp] is an
     *  odd number we then negate `result`. Finally we return a [UnifiedReal] constructed from
     *  `result`.
     *  - Equal to 0 (within the tolerance of [DEFAULT_COMPARE_TOLERANCE]), if [exp] is negative we
     *  return a [UnifiedReal] constructed from the [CR] that results when we take the [CR] that our
     *  [recursivePow] method calculates for our value as a [CR] raised to minus [exp] and inverting
     *  it. If [exp] is positive (or zero) we return a [UnifiedReal] constructed from the [CR] that
     *  our [recursivePow] method calculates for our value as a [CR] raised to [exp].
     *
     * @param exp the [BigInteger] power we are to raise *this* to.
     * @return a [UnifiedReal] which is *this* raised to the [exp] power.
     */
    private fun expLnPow(exp: BigInteger): UnifiedReal {
        val sign = signum(DEFAULT_COMPARE_TOLERANCE)
        when {
            sign > 0 -> // Safe to take the log. This avoids deep recursion for huge exponents, which
                // may actually make sense here.
                return UnifiedReal(crValue().ln().multiply(CR.valueOf(exp)).exp())
            sign < 0 -> {
                var result = crValue().negate().ln().multiply(CR.valueOf(exp)).exp()
                if (exp.testBit(0) /* odd exponent */) {
                    result = result.negate()
                }
                return UnifiedReal(result)
            }
            else -> // Base of unknown sign with integer exponent. Use a recursive computation.
                // (Another possible option would be to use the absolute value of the base, and then
                // adjust the sign at the end.  But that would have to be done in the CR
                // implementation.)
                return if (exp.signum() < 0) {
                    // This may be very expensive if exp.negate() is large.
                    UnifiedReal(recursivePow(crValue(), exp.negate()).inverse())
                } else {
                    UnifiedReal(recursivePow(crValue(), exp))
                }
        }
    }

    /**
     * Compute an integral power of this. This recurses roughly as deeply as the number of bits in
     * the exponent, and can, in ridiculous cases, result in a stack overflow. If [exp] is the
     * constant [BigInteger.ONE] we just return *this*. If [exp] is 0 (as determined by its [signum]
     * method) we return the constant [ONE]. Otherwise we initialize our `val absExp` to the absolute
     * value of [exp], and then if our [mCrFactor] points to the constant [CR_ONE] and `absExp` is
     * less than [HARD_RECURSIVE_POW_LIMIT] (as determined by the `compareTo` method of `absExp`) we
     * initialize our `val ratPow` to the [BoundedRational] returned by the `pow` method of our
     * field [mRatFactor] when it raises [mRatFactor] to the [exp] power. If `ratPow` is not *null*
     * we return a [UnifiedReal] constructed from it. If the above shortcuts fail to do the work we
     * first check if `absExp` is greater than [RECURSIVE_POW_LIMIT] and if it is we return the
     * [UnifiedReal] returned by our [expLnPow] when it raises *this* to the [exp] power. Otherwise
     * `absExp` is less than or equal to [RECURSIVE_POW_LIMIT] so we initialize our `val square` to
     * the [BoundedRational] returned by our [getSquare] method when it tries to find a small integer
     * which our [mCrFactor] field is the square root of. If `square` is not *null* we initialize our
     * `val nRatFactor` to the [BoundedRational] that results when we multiply our [mRatFactor] raised
     * to the power [exp] and `square` raised to half of [exp]. If `nRatFactor` is not *null* we
     * return a [UnifiedReal] constructed from `nRatFactor` and our [mCrFactor] field if [exp] is
     * odd, and if it is even we return a [UnifiedReal] constructed from `nRatFactor`. If `square`
     * is *null* we return the [UnifiedReal] returned by our [expLnPow] method when it raises *this*
     * to the [exp] power.
     *
     * @param exp the [BigInteger] power we are to raise *this* to.
     * @return a [UnifiedReal] which is *this* raised to the [exp] power.
     */
    private fun pow(exp: BigInteger): UnifiedReal {
        if (exp == BigInteger.ONE) {
            return this
        }
        if (exp.signum() == 0) {
            // Questionable if base has undefined value or is 0.
            // Java.lang.Math.pow() returns 1 anyway, so we do the same.
            return ONE
        }
        val absExp = exp.abs()
        @Suppress("ReplaceCallWithBinaryOperator")
        if (mCrFactor === CR_ONE && absExp.compareTo(HARD_RECURSIVE_POW_LIMIT) <= 0) {
            val ratPow = mRatFactor.pow(exp)
            // We count on this to fail, e.g. for very large exponents, when it would
            // otherwise be too expensive.
            if (ratPow != null) {
                return UnifiedReal(ratPow)
            }
        }
        @Suppress("ReplaceCallWithBinaryOperator")
        if (absExp.compareTo(RECURSIVE_POW_LIMIT) > 0) {
            return expLnPow(exp)
        }
        val square = getSquare(mCrFactor)
        if (square != null) {
            val nRatFactor = BoundedRational.multiply(mRatFactor.pow(exp)!!, square.pow(exp.shiftRight(1)))
            if (nRatFactor != null) {
                return if (exp.and(BigInteger.ONE).toInt() == 1) {
                    // Odd power: Multiply by remaining square root.
                    UnifiedReal(nRatFactor, mCrFactor)
                } else {
                    UnifiedReal(nRatFactor)
                }
            }
        }
        return expLnPow(exp)
    }

    /**
     * Return this ^ expon. This is really only well-defined for a positive base, particularly since
     * 0^x is not continuous at zero. (0^0 = 1 (as is epsilon^0), but 0^epsilon is 0. We nonetheless
     * try to do reasonable things at zero, when we recognize that case. If our [mCrFactor] field
     * points the constant [CR_E] we return the result of the `exp` method of [expon] when it raises
     * the mathematical constant _e_ to the [expon] power if our [mRatFactor] field is equal to
     * [BoundedRational.ONE], and if it is not equal to that we initialize our `val ratPart` to a
     * [UnifiedReal] which is the result of raising a [UnifiedReal] constructed from our [mRatFactor]
     * field to the power [expon], then return the result of multiplying `ratPart` by the result of
     * the `exp` method of [expon] when it raises the mathematical constant _e_ to the [expon] power.
     *
     * If our [mCrFactor] field is not [CR_E] we initialize our `val expAsBR` to the [BoundedRational]
     * returned by the `boundedRationalValue` method of [expon] and if this is not *null* we
     * initialize our `var expAsBI` to the [BigInteger] that the [BoundedRational.asBigInteger] method
     * returns for `expAsBR`, and if `expAsBI` is not *null* we return the [UnifiedReal] that our
     * [pow] method returns when it raises *this* to the `expAsBI` power. If `expAsBI` is *null* we
     * set `expAsBI` to the [BoundedRational] we set it to the [BoundedRational] that our method
     * [BoundedRational.asBigInteger] for the result of multiplying `expAsBR` by the constant
     * [BoundedRational.TWO] (just in case [expon] is a multiple of a half) and if `expAsBI` is not
     * *null* we return the square root of the [UnifiedReal] that our [pow] method returns when it
     * raises *this* to the `expAsBI` power.
     *
     * Having reached this point without a simple way to calculate our result we double check whether
     * our [definitelyZero] method determines that we are definitely equal to 0, and if so return
     * the constant [ZERO]. If not we initialize our `val sign` to the [Int] returned by our [signum]
     * method when it compares *this* to 0 to the bit tolerance [DEFAULT_COMPARE_TOLERANCE]. If `sign`
     * is less than 0 (we are negative) we throw an [ArithmeticException] "Negative base for pow()
     * with non-integer exponent". Otherwise we return a [UnifiedReal] that is constructed from the
     * [CR] that results when we take the natural log of our value as a [CR], multiply it by the value
     * of [expon] as a [CR] and then raise the mathematical constant _e_ to the resulting [CR] power
     * (the inverse natural log).
     *
     * @param expon the [UnifiedReal] power that we are to raise *this* to.
     * @return a [UnifiedReal] which is the result of raising *this* to the power [expon].
     */
    fun pow(expon: UnifiedReal): UnifiedReal {
        if (mCrFactor === CR_E) {
            return if (mRatFactor == BoundedRational.ONE) {
                expon.exp()
            } else {
                val ratPart = UnifiedReal(mRatFactor).pow(expon)
                expon.exp().multiply(ratPart)
            }
        }
        val expAsBR = expon.boundedRationalValue()
        if (expAsBR != null) {
            var expAsBI = BoundedRational.asBigInteger(expAsBR)
            if (expAsBI != null) {
                return pow(expAsBI)
            } else {
                // Check for exponent that is a multiple of a half.
                expAsBI = BoundedRational.asBigInteger(
                        BoundedRational.multiply(BoundedRational.TWO, expAsBR))
                if (expAsBI != null) {
                    return pow(expAsBI).sqrt()
                }
            }
        }
        // If the exponent were known zero, we would have handled it above.
        if (definitelyZero()) {
            return ZERO
        }
        val sign = signum(DEFAULT_COMPARE_TOLERANCE)
        if (sign < 0) {
            throw ArithmeticException("Negative base for pow() with non-integer exponent")
        }
        return UnifiedReal(crValue().ln().multiply(expon.crValue()).exp())
    }

    /**
     * Returns the natural log of *this*. If our [mCrFactor] field points to the constant [CR_E] we
     * return the result of adding the constant [ONE] to the natual log of a [UnifiedReal] constructed
     * from our [mRatFactor] field and the constant [CR_ONE]. Otherwise we first check if we are
     * comparable to the constant [ZERO], and if we are we check whether we are less than or equal
     * to 0, and if so we throw an [ArithmeticException] "log(non-positive)". If we are greater than
     * 0 we initialize our `val compare1` to the [Int] returned by our [compareTo] method when it
     * compares *this* to the constant [ONE] with [DEFAULT_COMPARE_TOLERANCE] bits of tolerance. If
     * `compare1` is equal to 0 we check if we definitely equal to [ONE] and if so we return the
     * constant [ZERO]. And if `compare1` is less than 0 we return the negation of the natural log
     * of our inverse. If the above special cases fail to return a value we initialize our `val bi`
     * to the [BigInteger] version of our [mRatFactor] field (if one exists) and if `bi` is not
     * *null* we check if our [mCrFactor] field points to the constant [CR_ONE] and if so we loop
     * over `i` for all the [CR] values in our [sLogs] array of powers of small integers, and if
     * the `i`th entry in `sLogs` is not *null* we initialize our `val intLog` to the [Long] returned
     * by our [getIntLog] method when it attempts to find an integral log for `bi` with respect to
     * the base `i` (it returns 0 if it can't find one). If `intLog` is not 0 we return a [UnifiedReal]
     * constructed from a [BoundedRational] constructed from `intLog` and the `i`th entry in [sLogs].
     * If [mCrFactor] does not point to the constant [CR_ONE] we want to check for n^k * sqrt(n), so
     * we initialize our `val square` to the [BoundedRational] that our [getSquare] method finds for
     * our [mCrFactor] field when it searches our [sSqrts] array of the square root of small integers
     * (if any exists). If `square` is not *null* we initialize our `val intSquare` to the [Int] that
     * the `intValue` method of `square` returns as the [Int] value of `square`. Then if the `intSquare`
     * entry in our [sLogs] array of the natural logs of small integers is not *null* we initialize
     * our `val intLog` to the integral log of `bi` for the base of `intSquare` that our [getIntLog]
     * method returns (it returns 0 if this does not exist). If `intLog` is not 0 we initialize our
     * `val nRatFactor` to the [BoundedRational] created by adding the [BoundedRational] constructed
     * from `intLog` to the constant [BoundedRational.HALF], and then if `nRatFactor` is not *null*
     * we return a [UnifiedReal] constructed from `nRatFactor` and the `intSquare` entry in [sLogs].
     * If none of the above attempt to find a more useful answer succeeds we return the natural log
     * of a [UnifiedReal] constructed from our value as a [CR].
     *
     * @return a [UnifiedReal] which is the natural log of *this*
     */
    fun ln(): UnifiedReal {
        if (mCrFactor === CR_E) {
            return UnifiedReal(mRatFactor, CR_ONE).ln().add(ONE)
        }
        if (isComparable(ZERO)) {
            if (signum() <= 0) {
                throw ArithmeticException("log(non-positive)")
            }
            val compare1 = compareTo(ONE, DEFAULT_COMPARE_TOLERANCE)
            if (compare1 == 0) {
                if (definitelyEquals(ONE)) {
                    return ZERO
                }
            } else if (compare1 < 0) {
                return inverse().ln().negate()
            }
            val bi = BoundedRational.asBigInteger(mRatFactor)
            if (bi != null) {
                if (mCrFactor === CR_ONE) {
                    // Check for a power of a small integer.  We can use sLogs[] to return
                    // a more useful answer for those.
                    for (i in sLogs.indices) {
                        if (sLogs[i] != null) {
                            val intLog = getIntLog(bi, i)
                            if (intLog != 0L) {
                                return UnifiedReal(BoundedRational(intLog), sLogs[i]!!)
                            }
                        }
                    }
                } else {
                    // Check for n^k * sqrt(n), for which we can also return a more useful answer.
                    val square = getSquare(mCrFactor)
                    if (square != null) {
                        val intSquare = square.intValue()
                        if (sLogs[intSquare] != null) {
                            val intLog = getIntLog(bi, intSquare)
                            if (intLog != 0L) {
                                val nRatFactor = BoundedRational.add(BoundedRational(intLog),
                                        BoundedRational.HALF)
                                if (nRatFactor != null) {
                                    return UnifiedReal(nRatFactor, sLogs[intSquare]!!)
                                }
                            }
                        }
                    }
                }
            }
        }
        return UnifiedReal(crValue().ln())
    }

    /**
     * Raises the mathematical constant _e_ (base of the natural logarithm) to the power *this* and
     * returns the result. If our [definitelyEquals] method determines that we are definitely equal
     * to the constant [ZERO] we return the constant [ONE]. If our [definitelyEquals] method determines
     * that we are definitely equal to the constant [ONE] we return the constant [E]. Otherwise we
     * initialize our `val crExp` to the [BoundedRational] our [getExp] method returns for our field
     * [mCrFactor] after it searches the [sLogs] array of the logs of small integers (if any). Then
     * if `crExp` is not *null* we initialize our `var needSqrt` to *false*, our `var ratExponent`
     * to our [mRatFactor] field and our `val asBI` the the [BigInteger] value of `ratExponent`.
     * If `asBI` is *null* we might still be a multiple of one half so we set `needSqrt` to *true*
     * and double `ratExponent`. In either case we set our `val nRatFactor` to the [BoundedRational]
     * that results after the [BoundedRational.pow] method raises `crExp` to the integral power
     * `ratExponent`. If `nRatFactor` is not *null* we initialize our `var result` to a [UnifiedReal]
     * constructed from `nRatFactor` then setting `result` to the square root of `result` if `needSqrt`
     * is *true*, then returning `result` to the caller. If none of the above short cuts click we
     * return a [UnifiedReal] constructed from the value returned by the `exp` method of our value
     * as a [CR].
     *
     * @return the mathematical constant _e_ raised to *this*.
     */
    fun exp(): UnifiedReal {
        if (definitelyEquals(ZERO)) {
            return ONE
        }
        if (definitelyEquals(ONE)) {
            // Avoid redundant computations, and ensure we recognize all instances as equal.
            return E
        }
        val crExp = getExp(mCrFactor)
        if (crExp != null) {
            var needSqrt = false
            var ratExponent: BoundedRational? = mRatFactor
            val asBI = BoundedRational.asBigInteger(ratExponent)
            if (asBI == null) {
                // check for multiple of one half.
                needSqrt = true
                ratExponent = BoundedRational.multiply(ratExponent!!, BoundedRational.TWO)
            }
            val nRatFactor = BoundedRational.pow(crExp, ratExponent)
            if (nRatFactor != null) {
                var result = UnifiedReal(nRatFactor)
                if (needSqrt) {
                    result = result.sqrt()
                }
                return result
            }
        }
        return UnifiedReal(crValue().exp())
    }


    /**
     * Factorial function. Fails if argument is clearly not an integer. May round to nearest integer
     * if value is close. First we initialize our `var asBI` to our value as a [BigInteger] if it
     * exists. If `asBI` is *null* we set `asBI` to the approximate integer value of our value as a
     * [CR] that the [CR.approxGet] method returns for a precision of 0. If a [UnifiedReal] constructed
     * from `asBI` is not approximately equal to *this* to a precision of [DEFAULT_COMPARE_TOLERANCE]
     * bits we throw an [ArithmeticException] "Non-integral factorial argument". `asBI` is not now
     * *null* in any case so we check if `asBI` is less than 0 and throw an [ArithmeticException]
     * "Negative factorial argument" if it is. Next we check if the bit length of `asBI` is greater
     * than 20 and if so we throw an [ArithmeticException] "Factorial argument too big". If we got
     * this far we are pretty sure we calculate the factorial of *this*, so we initialize our
     * `val biResult` to the [BigInteger] that our [genFactorial] method calculates for the [Long]
     * value of `asBI` using a step size of 1. We then initialize our `val nRatFactor` to a
     * [BoundedRational] constructed from `biResult` and return a [UnifiedReal] constructed from
     * `nRatFactor`.
     *
     * @return a [UnifiedReal] which is the factorial of *this*
     */
    fun fact(): UnifiedReal {
        var asBI = bigIntegerValue()
        if (asBI == null) {
            asBI = crValue().approxGet(0)  // Correct if it was an integer.
            if (!approxEquals(UnifiedReal(asBI), DEFAULT_COMPARE_TOLERANCE)) {
                throw ArithmeticException("Non-integral factorial argument")
            }
        }
        if (asBI!!.signum() < 0) {
            throw ArithmeticException("Negative factorial argument")
        }
        if (asBI.bitLength() > 20) {
            // Will fail.  LongValue() may not work. Punt now.
            throw ArithmeticException("Factorial argument too big")
        }
        val biResult = genFactorial(asBI.toLong(), 1)
        val nRatFactor = BoundedRational(biResult)
        return UnifiedReal(nRatFactor)
    }

    /**
     * Return the number of decimal digits to the right of the decimal point required to represent
     * the argument exactly. Return [Integer.MAX_VALUE] if that's not possible. Never returns a value
     * less than zero, even if r is a power of ten. If our [mCrFactor] field points to the constant
     * [CR_ONE] or our [mRatFactor] field is 0 we return the number of digits required to exactly
     * display our field [mRatFactor] that is returned by our [BoundedRational.digitsRequired] method.
     * Otherwise we return [Integer.MAX_VALUE].
     *
     * @return the number of decimal digits to the right of the decimal point required to represent
     * the argument exactly, or [Integer.MAX_VALUE] if that is not possible.
     */
    fun digitsRequired(): Int {
        return if (mCrFactor === CR_ONE || mRatFactor.signum() == 0) {
            BoundedRational.digitsRequired(mRatFactor)
        } else {
            Integer.MAX_VALUE
        }
    }

    /**
     * Return an upper bound on the number of leading zero bits. These are the number of 0 bits to
     * the right of the binary point and to the left of the most significant digit. Returns
     * [Integer.MAX_VALUE] if we cannot bound it. If our [mCrFactor] field points to one of the
     * well known [CR] constants we have a chance to place a bound on the number of leading zero
     * bits and if so we initialize our `val wholeBits` to the approximate number of bits to left of
     * the binary point of our [mRatFactor] field. If `wholeBits` is equal to [Integer.MIN_VALUE]
     * (the field is 0) we return [Integer.MAX_VALUE], otherwise if `wholeBits` is greater than or
     * equal to 3 we return 0, and if it is less then 3 we return 3 minus `wholeBits`. On the other
     * hand if our [mCrFactor] is not a well known [CR] we return [Integer.MAX_VALUE].
     *
     * @return an upper bound on the number of leading zero bits in *this*
     */
    fun leadingBinaryZeroes(): Int {
        if (isNamed(mCrFactor)) {
            // Only ln(2) is smaller than one, and could possibly add one zero bit.
            // Adding 3 gives us a somewhat sloppy upper bound.
            val wholeBits = mRatFactor.wholeNumberBits()
            if (wholeBits == Integer.MIN_VALUE) {
                return Integer.MAX_VALUE
            }
            return if (wholeBits >= 3) {
                0
            } else {
                -wholeBits + 3
            }
        }
        return Integer.MAX_VALUE
    }

    /**
     * Is the number of bits to the left of the decimal point greater than bound? The result is
     * inexact: We roughly approximate the whole number bits. If our [mCrFactor] field points to one
     * of the well know [CR] constants we return *true* if the number of bits to the left of the
     * decimal point in our [mRatFactor] field is greater than [bound]. Otherwise we return *true*
     * if the bit length of the approximate value or our value as a [CR] to a precision of [bound]
     * minus 2 is greater than 2.
     *
     * @param bound the number of bits we wish to compare with the number of bits to the left of the
     * decimal point of *this*.
     * @return *true* if there are more bits to the left of the decimal point in *this* than [bound]
     */
    fun approxWholeNumberBitsGreaterThan(bound: Int): Boolean {
        return if (isNamed(mCrFactor)) {
            mRatFactor.wholeNumberBits() > bound
        } else {
            crValue().approxGet(bound - 2).bitLength() > 2
        }
    }

    /**
     * Our static constants and methods.
     */
    companion object {
        // TODO: It would be helpful to add flags to indicate whether the result is known
        // irrational, etc.  This sometimes happens even if mCrFactor is not one of the known ones.
        // And exact comparisons between rationals and known irrationals are decidable.

        /**
         * Perform some nontrivial consistency checks.
         */
        @Suppress("unused")
        var enableChecks = true

        /**
         * Throws an [AssertionError] if the argument is *false*
         *
         * @param b if *false* we throw an [AssertionError].
         */
        private fun check(b: Boolean) {
            if (!b) {
                throw AssertionError()
            }
        }

        /**
         * Returns a [UnifiedReal] constructed to be equal to its [Double] argument [x]. If [x] is
         * equal to 0.0 or to 1.0 we return the value returned by our overloaded [valueOf] brother
         * method for the [Long] value of [x], otherwise we return a [UnifiedReal] constructed from
         * the [BoundedRational] value of [x] returned by the [BoundedRational.valueOf] method.
         *
         * @param x the [Double] whose value the [UnifiedReal] we return will hold.
         * @return a [UnifiedReal] which is equal to our [Double] argument [x].
         */
        @Suppress("unused")
        fun valueOf(x: Double): UnifiedReal {
            return if (x == 0.0 || x == 1.0) {
                valueOf(x.toLong())
            } else UnifiedReal(BoundedRational.valueOf(x))
        }

        /**
         * Returns a [UnifiedReal] constructed to be equal to its [Long] argument [x]. If [x] is
         * equal to 0L we return the constant [ZERO], if it is equal to 1L return the constant [ONE]
         * otherwise we return a [UnifiedReal] constructed from the [BoundedRational] value of [x]
         * returned by the [BoundedRational.valueOf] method.
         *
         * @param x the [Long] whose value the [UnifiedReal] we return will hold.
         * @return a [UnifiedReal] which is equal to our [Long] argument [x].
         */
        fun valueOf(x: Long): UnifiedReal {
            return when (x) {
                0L -> ZERO
                1L -> ONE
                else -> UnifiedReal(BoundedRational.valueOf(x))
            }
        }

        // Various helpful constants

        /**
         * The [BigInteger] of the integer 24, used by our [piTwelfths] method.
         */
        private val BIG_24 = BigInteger.valueOf(24)
        /**
         * The number of bits of precision tolerance we use for comparing some values.
         */
        private const val DEFAULT_COMPARE_TOLERANCE = -1000

        // Well-known CR constants we try to use in the mCrFactor position:

        /**
         * The [CR] constant 1.0
         */
        private val CR_ONE = CR.ONE
        /**
         * The [CR] constant pi
         */
        private val CR_PI = CR.PI
        /**
         * The [CR] constant for _e_, the base of the natural logarithm
         */
        private val CR_E = CR.ONE.exp()
        /**
         * The [CR] constant for the square root of 2.0
         */
        private val CR_SQRT2 = CR.valueOf(2).sqrt()
        /**
         * The [CR] constant for the square root of 3.0
         */
        private val CR_SQRT3 = CR.valueOf(3).sqrt()
        /**
         * The [CR] constant for the natural log of 2.0
         */
        private val CR_LN2 = CR.valueOf(2).ln()
        /**
         * The [CR] constant for the natural log of 3.0
         */
        private val CR_LN3 = CR.valueOf(3).ln()
        /**
         * The [CR] constant for the natural log of 5.0
         */
        private val CR_LN5 = CR.valueOf(5).ln()
        /**
         * The [CR] constant for the natural log of 6.0
         */
        private val CR_LN6 = CR.valueOf(6).ln()
        /**
         * The [CR] constant for the natural log of 7.0
         */
        private val CR_LN7 = CR.valueOf(7).ln()
        /**
         * The [CR] constant for the natural log of 10.0
         */
        private val CR_LN10 = CR.valueOf(10).ln()

        /**
         * Square roots that we try to recognize. We currently recognize only a small fixed
         * collection, since the sqrt() function needs to identify numbers of the form:
         * SQRT[ i ]*n^2, and we don't otherwise know of a good algorithm for that.
         */
        @Suppress("RemoveExplicitTypeArguments")
        private val sSqrts = arrayOf<CR?>(
                null, CR.ONE, CR_SQRT2, CR_SQRT3, null, CR.valueOf(5).sqrt(),
                CR.valueOf(6).sqrt(), CR.valueOf(7).sqrt(), null, null,
                CR.valueOf(10).sqrt()
        )

        /**
         * Natural logs of small integers that we try to recognize.
         */
        @Suppress("RemoveExplicitTypeArguments")
        private val sLogs = arrayOf<CR?>(
                null, null, CR_LN2, CR_LN3, null, CR_LN5,
                CR_LN6, CR_LN7, null, null, CR_LN10
        )

        // Some convenient UnifiedReal constants.

        /**
         * The [UnifiedReal] for the constant pi.
         */
        val PI = UnifiedReal(CR_PI)
        /**
         * The [UnifiedReal] for the mathematical constant _e_, base of the natural logarithms.
         */
        val E = UnifiedReal(CR_E)
        /**
         * The [UnifiedReal] for the integer constant 0
         */
        val ZERO = UnifiedReal(BoundedRational.ZERO)
        /**
         * The [UnifiedReal] for the integer constant 1
         */
        val ONE = UnifiedReal(BoundedRational.ONE)
        /**
         * The [UnifiedReal] for the integer constant -1
         */
        val MINUS_ONE = UnifiedReal(BoundedRational.MINUS_ONE)
        /**
         * The [UnifiedReal] for the integer constant 2
         */
        val TWO = UnifiedReal(BoundedRational.TWO)
        /**
         * The [UnifiedReal] for the integer constant -2
         */
        @Suppress("unused")
        val MINUS_TWO = UnifiedReal(BoundedRational.MINUS_TWO)
        /**
         * The [UnifiedReal] for the rational constant 1/2
         */
        val HALF = UnifiedReal(BoundedRational.HALF)
        /**
         * The [UnifiedReal] for the rational constant -1/2
         */
        @Suppress("unused")
        val MINUS_HALF = UnifiedReal(BoundedRational.MINUS_HALF)
        /**
         * The [UnifiedReal] for the integer constant 10
         */
        val TEN = UnifiedReal(BoundedRational.TEN)
        /**
         * The multiplier for converting degrees to radians, used in the [CalculatorExpr.toRadians]
         * method and the [CalculatorExpr.fromRadians] method.
         */
        val RADIANS_PER_DEGREE = UnifiedReal(BoundedRational(1, 180), CR_PI)
        /**
         * The [UnifiedReal] for the integer constant 6
         */
        @Suppress("unused")
        private val SIX = UnifiedReal(6)
        /**
         * The [UnifiedReal] for the constant one half of the square root of 2.0
         */
        private val HALF_SQRT2 = UnifiedReal(BoundedRational.HALF, CR_SQRT2)
        /**
         * The [UnifiedReal] for the constant square root of 3.0
         */
        private val SQRT3 = UnifiedReal(CR_SQRT3)
        /**
         * The [UnifiedReal] for the constant 1/2 of the square root of 3.0
         */
        private val HALF_SQRT3 = UnifiedReal(BoundedRational.HALF, CR_SQRT3)
        /**
         * The [UnifiedReal] for the constant 1/3 of the square root of 3.0
         */
        private val THIRD_SQRT3 = UnifiedReal(BoundedRational.THIRD, CR_SQRT3)
        /**
         * The [UnifiedReal] for the constant 1/2 of pi.
         */
        private val PI_OVER_2 = UnifiedReal(BoundedRational.HALF, CR_PI)
        /**
         * The [UnifiedReal] for the constant 1/3 of pi.
         */
        private val PI_OVER_3 = UnifiedReal(BoundedRational.THIRD, CR_PI)
        /**
         * The [UnifiedReal] for the constant 1/4 of pi.
         */
        private val PI_OVER_4 = UnifiedReal(BoundedRational.QUARTER, CR_PI)
        /**
         * The [UnifiedReal] for the constant 1/6 of pi.
         */
        private val PI_OVER_6 = UnifiedReal(BoundedRational.SIXTH, CR_PI)


        /**
         * Given a constructive real [cr], try to determine whether [cr] is the square root of a
         * small integer. If so, return its square as a [BoundedRational]. Otherwise return null.
         * We make this determination by simple table lookup, so spurious *null* returns are
         * entirely possible, or even likely. We loop over `i` for all of the indices into our
         * array [sSqrts] and if [cr] points to the same [CR] as the `i`'th entry in [sSqrts] we
         * return a [BoundedRational] constructed from `i` to the caller. If none of the entries
         * match we return *null*.
         *
         * @param cr the [CR] we are to search for in our [sSqrts] array of known square roots.
         * @return A [BoundedRational] constructed from the small integer that [cr] is the square
         * root of, or *null* if we did not find it.
         */
        private fun getSquare(cr: CR): BoundedRational? {
            for (i in sSqrts.indices) {
                if (sSqrts[i] === cr) {
                    return BoundedRational(i.toLong())
                }
            }
            return null
        }

        /**
         * If the argument is a well-known constructive real, return its name. No named constructive
         * reals are rational multiples of each other. Thus two [UnifiedReal]'s with different named
         * [mCrFactor]'s can be equal only if both [mRatFactor]'s are zero or possibly if one is
         * [CR_PI] and the other is [CR_E] (the latter is apparently an open problem).
         *
         * If our parameter [cr] points to the constant [CR_ONE] we return the empty string. If our
         * parameter [cr] points to the constant [CR_PI] we return the utf8 character for the Greek
         * small letter pi. If our parameter [cr] points to the constant [CR_E] we return the string
         * "e". Next we loop over `i` searching through all the [CR] constant entries in our array
         * [sSqrts] and if we find an `i`'th entry which [cr] points to we return a string consisting
         * of the utf8 character for the square root symbol concatenated to the string value of `i`.
         * Next we loop over `i` searching through all the [CR] constant entries in our array [sLogs]
         * and if we find an `i`'th entry which [cr] points to we return a string formed by surrounding
         * the string value of `i` in a "ln()" function. If we have not found [cr] our lists of well
         * known [CR] constants we return *null* to the caller.
         *
         * @param cr the [CR] we are attempting to find a well known name for.
         * @return a [String] corresponding to the name of a well-known constructive real which [cr]
         * points to, or *null* if there is none found.
         */
        private fun crName(cr: CR): String? {
            if (cr === CR_ONE) {
                return ""
            }
            if (cr === CR_PI) {
                return "\u03C0"   // GREEK SMALL LETTER PI
            }
            if (cr === CR_E) {
                return "e"
            }
            for (i in sSqrts.indices) {
                if (cr === sSqrts[i]) {
                    return "\u221A" /* SQUARE ROOT */ + i
                }
            }
            for (i in sLogs.indices) {
                if (cr === sLogs[i]) {
                    return "ln($i)"
                }
            }
            return null
        }

        /**
         * Would our [crName] method return non-Null? If [cr] points to one of the constants [CR_ONE],
         * [CR_PI], or [CR_E] we return *true*. If [cr] points to one of the entries in our array
         * [sSqrts] we return *true*, and if [cr] points to one of the entries in our array [sLogs]
         * we return *true*. Otherwise we return *false*.
         *
         * @param cr the [CR] we are attempting to find in our lists of well known [CR] constants.
         * @return *true* if [cr] is one of our well known [CR] constants, otherwise *false*
         */
        private fun isNamed(cr: CR): Boolean {
            if (cr === CR_ONE || cr === CR_PI || cr === CR_E) {
                return true
            }
            for (r in sSqrts) {
                if (cr === r) {
                    return true
                }
            }
            for (r in sLogs) {
                if (cr === r) {
                    return true
                }
            }
            return false
        }

        /**
         * Is [cr] known to be algebraic (as opposed to transcendental)? Currently only produces
         * meaningful results for the above known special constructive reals.
         *
         * @param cr the [CR] we are to test to see if it is algebraic.
         * @return *true* if [cr] points to the constant [CR_ONE] or our [getSquare] method finds it
         * amongst our list of the square roots of small integers.
         */
        private fun definitelyAlgebraic(cr: CR): Boolean {
            return cr === CR_ONE || getSquare(cr) != null
        }


        /**
         * Is it known that the two constructive reals differ by something other than a a rational
         * factor, i.e. is it known that two [UnifiedReal]'s with those [mCrFactor]'s will compare
         * unequal unless both [mRatFactor]'s are zero? If this returns *true*, then a comparison
         * of two [UnifiedReal]'s using those two [mCrFactor]'s cannot diverge, though we don't know
         * of a good runtime bound. If [r1] points to the same [CR] as [r2] we return *false*. If
         * [r1] points to the constant [CR_E] or to the constant [CR_PI] we return the value returned
         * by our [definitelyAlgebraic] method for [r2], and if [r2] points to the constant [CR_E] or
         * to the constant [CR_PI] we return the value returned by our [definitelyAlgebraic] method
         * for [r1]. And finally we return *true* if both [r1] and [r2] point to one of our well known
         * [CR] constants.
         *
         * @param r1 the first [CR] we are to consider.
         * @param r2 the first [CR] we are to consider.
         * @return *false* if [r1] and [r2] are not independent, *true* if they are
         */
        private fun definitelyIndependent(r1: CR, r2: CR): Boolean {
            // The question here is whether r1 = x*r2, where x is rational, where r1 and r2
            // are in our set of special known CRs, can have a solution.
            // This cannot happen if one is CR_ONE and the other is not.
            // (Since all others are irrational.)
            // This cannot happen for two named square roots, which have no repeated factors.
            // (To see this, square both sides of the equation and factor.  Each prime
            // factor in the numerator and denominator occurs twice.)
            // This cannot happen for e or pi on one side, and a square root on the other.
            // (One is transcendental, the other is algebraic.)
            // This cannot happen for two of our special natural logs.
            // (Otherwise ln(m) = (a/b)ln(n) ==> m = n^(a/b) ==> m^b = n^a, which is impossible
            // because either m or n includes a prime factor not shared by the other.)
            // This cannot happen for a log and a square root.
            // (The Lindemann-Weierstrass theorem tells us, among other things, that if
            // a is algebraic, then exp(a) is transcendental.  Thus if l in our finite
            // set of logs where algebraic, expl(l), must be transacendental.
            // But exp(l) is an integer.  Thus the logs are transcendental.  But of course the
            // square roots are algebraic.  Thus they can't be rational multiples.)
            // Unfortunately, we do not know whether e/pi is rational.
            if (r1 === r2) {
                return false
            }

            @Suppress("UNUSED_VARIABLE")
            val other: CR
            if (r1 === CR_E || r1 === CR_PI) {
                return definitelyAlgebraic(r2)
            }
            return if (r2 === CR_E || r2 === CR_PI) {
                definitelyAlgebraic(r1)
            } else isNamed(r1) && isNamed(r2)
        }

        /**
         * Number of extra bits used by our [toStringTruncated] method to decide whether to prefer
         * truncation to rounding. Must be <= 30.
         */
        private const val EXTRA_PREC = 10

        /**
         * Computer the sin() for an integer multiple n of pi/12, if easily representable.
         *
         * @param n value between 0 and 23 inclusive.
         * @return the sine of [n] times pi/12.
         */
        private fun sinPiTwelfths(n: Int): UnifiedReal? {
            if (n >= 12) {
                val negResult = sinPiTwelfths(n - 12)
                return negResult?.negate()
            }
            return when (n) {
                0 -> ZERO
                2 // 30 degrees
                -> HALF
                3 // 45 degrees
                -> HALF_SQRT2
                4 // 60 degrees
                -> HALF_SQRT3
                6 -> ONE
                8 -> HALF_SQRT3
                9 -> HALF_SQRT2
                10 -> HALF
                else -> null
            }
        }

        private fun cosPiTwelfths(n: Int): UnifiedReal? {
            var sinArg = n + 6
            if (sinArg >= 24) {
                sinArg -= 24
            }
            return sinPiTwelfths(sinArg)
        }

        /**
         * Return asin(n/2).  n is between -2 and 2.
         */
        fun asinHalves(n: Int): UnifiedReal {
            if (n < 0) {
                return asinHalves(-n).negate()
            }
            when (n) {
                0 -> return ZERO
                1 -> return UnifiedReal(BoundedRational.SIXTH, CR.PI)
                2 -> return UnifiedReal(BoundedRational.HALF, CR.PI)
            }
            throw AssertionError("asinHalves: Bad argument")
        }

        @Suppress("unused")
        private val BIG_TWO = BigInteger.valueOf(2)

        // The (in abs value) integral exponent for which we attempt to use a recursive
        // algorithm for evaluating pow(). The recursive algorithm works independent of the sign of the
        // base, and can produce rational results. But it can become slow for very large exponents.
        private val RECURSIVE_POW_LIMIT = BigInteger.valueOf(1000)
        // The corresponding limit when we're using rational arithmetic. This should fail fast
        // anyway, but we avoid ridiculously deep recursion.
        private val HARD_RECURSIVE_POW_LIMIT = BigInteger.ONE.shiftLeft(1000)

        /**
         * Compute an integral power of a constructive real, using the standard recursive algorithm.
         * exp is known to be positive.
         */
        private fun recursivePow(base: CR, exp: BigInteger): CR {
            if (exp == BigInteger.ONE) {
                return base
            }
            if (exp.testBit(0)) {
                return base.multiply(recursivePow(base, exp.subtract(BigInteger.ONE)))
            }
            val tmp = recursivePow(base, exp.shiftRight(1))
            if (Thread.interrupted()) {
                throw CR.AbortedException()
            }
            return tmp.multiply(tmp)
        }

        /**
         * Raise the argument to the 16th power.
         */
        private fun pow16(n: Int): Long {
            if (n > 10) {
                throw AssertionError("Unexpected pow16 argument")
            }
            var result = (n * n).toLong()
            result *= result
            result *= result
            result *= result
            return result
        }

        /**
         * Return the integral log with respect to the given base if it exists, 0 otherwise.
         * n is presumed positive.
         */
        private fun getIntLog(n: BigInteger, base: Int): Long {
            var nLocal = n
            val nAsDouble = nLocal.toDouble()
            val approx = ln(nAsDouble) / ln(base.toDouble())
            // A relatively quick test first.
            // Unfortunately, this doesn't help for values to big to fit in a Double.
            if (!java.lang.Double.isInfinite(nAsDouble) && abs(approx - round(approx)) > 1.0e-6) {
                return 0
            }
            var result: Long = 0

            @Suppress("UNUSED_VARIABLE") val remaining = nLocal
            val bigBase = BigInteger.valueOf(base.toLong())
            var base16th: BigInteger? = null  // base^16, computed lazily
            while (nLocal.mod(bigBase).signum() == 0) {
                if (Thread.interrupted()) {
                    throw CR.AbortedException()
                }
                nLocal = nLocal.divide(bigBase)
                ++result
                // And try a slightly faster computation for large n:
                if (base16th == null) {
                    base16th = BigInteger.valueOf(pow16(base))
                }
                while (nLocal.mod(base16th).signum() == 0) {
                    nLocal = nLocal.divide(base16th)
                    result += 16
                }
            }
            return if (nLocal == BigInteger.ONE) {
                result
            } else 0
        }


        /**
         * Generalized factorial.
         * Compute n * (n - step) * (n - 2 * step) * etc.  This can be used to compute factorial a bit
         * faster, especially if BigInteger uses sub-quadratic multiplication.
         */
        private fun genFactorial(n: Long, step: Long): BigInteger {
            if (n > 4 * step) {
                val prod1 = genFactorial(n, 2 * step)
                if (Thread.interrupted()) {
                    throw CR.AbortedException()
                }
                val prod2 = genFactorial(n - step, 2 * step)
                if (Thread.interrupted()) {
                    throw CR.AbortedException()
                }
                return prod1.multiply(prod2)
            } else {
                if (n == 0L) {
                    return BigInteger.ONE
                }
                var res = BigInteger.valueOf(n)
                var i = n - step
                while (i > 1) {
                    res = res.multiply(BigInteger.valueOf(i))
                    i -= step
                }
                return res
            }
        }
    }
}

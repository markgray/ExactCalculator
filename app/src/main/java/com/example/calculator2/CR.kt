/*
 * Copyright (C) 2015 The Android Open Source Project
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

/*
 * The above license covers additions and changes by AOSP authors.
 * The original code is licensed as follows:
 */

/*
 *
 * Copyright (c) 1999, Silicon Graphics, Inc. -- ALL RIGHTS RESERVED
 *
 * Permission is granted free of charge to copy, modify, use and distribute
 * this software  provided you include the entirety of this notice in all
 * copies made.
 *
 * THIS SOFTWARE IS PROVIDED ON AN AS IS BASIS, WITHOUT WARRANTY OF ANY
 * KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, WITHOUT LIMITATION,
 * WARRANTIES THAT THE SUBJECT SOFTWARE IS FREE OF DEFECTS, MERCHANTABLE, FIT
 * FOR A PARTICULAR PURPOSE OR NON-INFRINGING.   SGI ASSUMES NO RISK AS TO THE
 * QUALITY AND PERFORMANCE OF THE SOFTWARE.   SHOULD THE SOFTWARE PROVE
 * DEFECTIVE IN ANY RESPECT, SGI ASSUMES NO COST OR LIABILITY FOR ANY
 * SERVICING, REPAIR OR CORRECTION.  THIS DISCLAIMER OF WARRANTY CONSTITUTES
 * AN ESSENTIAL PART OF THIS LICENSE. NO USE OF ANY SUBJECT SOFTWARE IS
 * AUTHORIZED HEREUNDER EXCEPT UNDER THIS DISCLAIMER.
 *
 * UNDER NO CIRCUMSTANCES AND UNDER NO LEGAL THEORY, WHETHER TORT (INCLUDING,
 * WITHOUT LIMITATION, NEGLIGENCE OR STRICT LIABILITY), CONTRACT, OR
 * OTHERWISE, SHALL SGI BE LIABLE FOR ANY DIRECT, INDIRECT, SPECIAL,
 * INCIDENTAL, OR CONSEQUENTIAL DAMAGES OF ANY CHARACTER WITH RESPECT TO THE
 * SOFTWARE INCLUDING, WITHOUT LIMITATION, DAMAGES FOR LOSS OF GOODWILL, WORK
 * STOPPAGE, LOSS OF DATA, COMPUTER FAILURE OR MALFUNCTION, OR ANY AND ALL
 * OTHER COMMERCIAL DAMAGES OR LOSSES, EVEN IF SGI SHALL HAVE BEEN INFORMED OF
 * THE POSSIBILITY OF SUCH DAMAGES.  THIS LIMITATION OF LIABILITY SHALL NOT
 * APPLY TO LIABILITY RESULTING FROM SGI's NEGLIGENCE TO THE EXTENT APPLICABLE
 * LAW PROHIBITS SUCH LIMITATION.  SOME JURISDICTIONS DO NOT ALLOW THE
 * EXCLUSION OR LIMITATION OF INCIDENTAL OR CONSEQUENTIAL DAMAGES, SO THAT
 * EXCLUSION AND LIMITATION MAY NOT APPLY TO YOU.
 *
 * These license terms shall be governed by and construed in accordance with
 * the laws of the United States and the State of California as applied to
 * agreements entered into and to be performed entirely within California
 * between California residents.  Any litigation relating to these license
 * terms shall be subject to the exclusive jurisdiction of the Federal Courts
 * of the Northern District of California (or, absent subject matter
 * jurisdiction in such courts, the courts of the State of California), with
 * venue lying exclusively in Santa Clara County, California.
 *
 * Copyright (c) 2001-2004, Hewlett-Packard Development Company, L.P.
 *
 * Permission is granted free of charge to copy, modify, use and distribute
 * this software  provided you include the entirety of this notice in all
 * copies made.
 *
 * THIS SOFTWARE IS PROVIDED ON AN AS IS BASIS, WITHOUT WARRANTY OF ANY
 * KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, WITHOUT LIMITATION,
 * WARRANTIES THAT THE SUBJECT SOFTWARE IS FREE OF DEFECTS, MERCHANTABLE, FIT
 * FOR A PARTICULAR PURPOSE OR NON-INFRINGING.   HEWLETT-PACKARD ASSUMES
 * NO RISK AS TO THE QUALITY AND PERFORMANCE OF THE SOFTWARE.
 * SHOULD THE SOFTWARE PROVE DEFECTIVE IN ANY RESPECT,
 * HEWLETT-PACKARD ASSUMES NO COST OR LIABILITY FOR ANY
 * SERVICING, REPAIR OR CORRECTION.  THIS DISCLAIMER OF WARRANTY CONSTITUTES
 * AN ESSENTIAL PART OF THIS LICENSE. NO USE OF ANY SUBJECT SOFTWARE IS
 * AUTHORIZED HEREUNDER EXCEPT UNDER THIS DISCLAIMER.
 *
 * UNDER NO CIRCUMSTANCES AND UNDER NO LEGAL THEORY, WHETHER TORT (INCLUDING,
 * WITHOUT LIMITATION, NEGLIGENCE OR STRICT LIABILITY), CONTRACT, OR
 * OTHERWISE, SHALL HEWLETT-PACKARD BE LIABLE FOR ANY DIRECT, INDIRECT, SPECIAL,
 * INCIDENTAL, OR CONSEQUENTIAL DAMAGES OF ANY CHARACTER WITH RESPECT TO THE
 * SOFTWARE INCLUDING, WITHOUT LIMITATION, DAMAGES FOR LOSS OF GOODWILL, WORK
 * STOPPAGE, LOSS OF DATA, COMPUTER FAILURE OR MALFUNCTION, OR ANY AND ALL
 * OTHER COMMERCIAL DAMAGES OR LOSSES, EVEN IF HEWLETT-PACKARD SHALL
 * HAVE BEEN INFORMED OF THE POSSIBILITY OF SUCH DAMAGES.
 * THIS LIMITATION OF LIABILITY SHALL NOT APPLY TO LIABILITY RESULTING
 * FROM HEWLETT-PACKARD's NEGLIGENCE TO THE EXTENT APPLICABLE
 * LAW PROHIBITS SUCH LIMITATION.  SOME JURISDICTIONS DO NOT ALLOW THE
 * EXCLUSION OR LIMITATION OF INCIDENTAL OR CONSEQUENTIAL DAMAGES, SO THAT
 * EXCLUSION AND LIMITATION MAY NOT APPLY TO YOU.
 *
 *
 * Added valueOf(string, radix), fixed some documentation comments.
 *              Hans_Boehm@hp.com 1/12/2001
 * Fixed a serious typo in InvCR():  For negative arguments it produced
 *              the wrong sign.  This affected the sign of divisions.
 * Added byteValue and fixed some comments.  Hans.Boehm@hp.com 12/17/2002
 * Added toStringFloatRep.      Hans.Boehm@hp.com 4/1/2004
 * Added approxGet() synchronization to allow access from multiple threads
 * hboehm@google.com 4/25/2014
 * Changed cos() pre-scaling to avoid logarithmic depth tree.
 * hboehm@google.com 6/30/2014
 * Added explicit asin() implementation.  Remove one.  Add ZERO and ONE and
 * make them public.  hboehm@google.com 5/21/2015
 * Added Gauss-Legendre PI implementation.  Removed two.
 * hboehm@google.com 4/12/2016
 * Fix shift operation in doubleValue. That produced incorrect values for
 * large negative exponents.
 * Don't negate argument and compute inverse for exp(). That causes severe
 * performance problems for (-huge).exp()
 * hboehm@google.com 8/21/2017
 * Have comparison check for interruption. hboehm@google.com 10/31/2017
 * Fix precision overflow issue in most general compareTo function.
 * Fix a couple of unused variable bugs. Notably selectorSign was
 * accidentally locally re-declared. (This turns out to be safe but useless.)
 * hboehm@google.com 11/20/2018.
 * Fix an exception-safety issue in GlPiCR.approximate.
 * hboehm@google.com 3/3/2019.
*/

package com.example.calculator2

import java.math.BigInteger
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Constructive real numbers, also known as recursive, or computable reals.
 * Each recursive real number is represented as an object that provides an
 * approximation function for the real number.
 * The approximation function guarantees that the generated approximation
 * is accurate to the specified precision.
 * Arithmetic operations on constructive reals produce new such objects;
 * they typically do not perform any real computation.
 * In this sense, arithmetic computations are exact: They produce
 * a description which describes the exact answer, and can be used to
 * later approximate it to arbitrary precision.
 *
 * When approximations are generated, *e.g.* for output, they are
 * accurate to the requested precision; no cumulative rounding errors
 * are visible.
 * In order to achieve this precision, the approximation function will often
 * need to approximate subexpressions to greater precision than was originally
 * demanded.  Thus the approximation of a constructive real number
 * generated through a complex sequence of operations may eventually require
 * evaluation to very high precision.  This usually makes such computations
 * prohibitively expensive for large numerical problems.
 * But it is perfectly appropriate for use in a desk calculator,
 * for small numerical problems, for the evaluation of expressions
 * computed by a symbolic algebra system, for testing of accuracy claims
 * for floating point code on small inputs, or the like.
 *
 * We expect that the vast majority of uses will ignore the particular
 * implementation, and the member functions `approximate`
 * and `approxGet`.  Such applications will treat `CR` as
 * a conventional numerical type, with an interface modeled on
 * `java.math.BigInteger`.  No subclasses of `CR`
 * will be explicitly mentioned by such a program.
 *
 * All standard arithmetic operations, as well as a few algebraic
 * and transcendental functions are provided.  Constructive reals are
 * immutable; thus all of these operations return a new constructive real.
 *
 * A few uses will require explicit construction of approximation functions.
 * The requires the construction of a subclass of `CR` with
 * an overridden `approximate` function.  Note that `approximate`
 * should only be defined, but never called.  `approxGet`
 * provides the same functionality, but adds the caching necessary to obtain
 * reasonable performance.
 *
 * Any operation may throw `AbortedException` if the thread
 * in which it is executing is interrupted.  (`InterruptedException`
 * cannot be used for this purpose, since `CR` inherits from `Number`.)
 *
 * Any operation may also throw `PrecisionOverflowException`
 * If the precision request generated during any sub-calculation overflows
 * a 28-bit integer.  (This should be extremely unlikely, except as an
 * outcome of a division by zero, or other erroneous computation.)
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "MemberVisibilityCanBePrivate")
abstract class CR : java.lang.Number() {

    /**
     * The smallest precision value with which the method [approximate] has been called.
     */
    @Transient
    internal var minPrec: Int = 0
    /**
     * The scaled approximation corresponding to minPrec.
     */
    @Transient
    internal var maxAppr: BigInteger? = null
    /**
     * minPrec and maxVal are valid.
     */
    @Transient
    internal var apprValid = false

    // CR is the basic representation of a number.
    // Abstractly this is a function for computing an approximation
    // plus the current best approximation.
    // We could do without the latter, but that would
    // be atrociously slow.

    /**
     * Indicates a constructive real operation was interrupted.
     * Most constructive real operations may throw such an exception.
     * This is unchecked, since Number methods may not raise checked
     * exceptions.
     */
    class AbortedException : RuntimeException {
        constructor() : super()

        @Suppress("unused")
        constructor(s: String) : super(s)
    }

    /**
     * Indicates that the number of bits of precision requested by
     * a computation on constructive reals required more than 28 bits,
     * and was thus in danger of overflowing an `int`.
     * This is likely to be a symptom of a diverging computation,
     * *e.g.* division by zero.
     */
    class PrecisionOverflowException : RuntimeException {
        constructor() : super()

        @Suppress("unused")
        constructor(s: String) : super(s)
    }

    /**
     * Must be defined in subclasses of [CR]. Most users can ignore the existence of this method,
     * and will not ever need to define a [CR] subclass. Returns value / 2 ** precision rounded to
     * an integer. The error in the result is strictly < 1. Informally, approximate(n) gives a
     * scaled approximation accurate to 2**n. Implementations may safely assume that precision is
     * at least a factor of 8 away from overflow. Called only with the lock on the [CR] object
     * already held.
     *
     * @param precision number of bits of precision required.
     * @return A [BigInteger] which is an approximation of our value with [precision] bits of
     * precision scaled by 2 ** [precision].
     */
    protected abstract fun approximate(precision: Int): BigInteger

    /**
     * Identical to approximate(), but maintain and update cache. Returns value / 2 ** prec rounded
     * to an integer. The error in the result is strictly < 1. Produces the same answer as
     * [approximate], but uses and maintains a cached approximation. Normally not overridden, and
     * called only from [approximate] methods in subclasses. Not needed if the provided operations
     * on constructive reals suffice. First we call our method [checkPrec] to check that [precision]
     * is at least a factor of 8 away from overflowing the integer used to hold a precision spec (it
     * throws [PrecisionOverflowException] if it is not). Then if our [apprValid] field is *true*
     * (there is a valid cached approximation available) and [precision] is greater than or equal to
     * our field [minPrec] (the precision of the cached value in [maxAppr] is better than needed) we
     * return [maxAppr] scaled by our [scale] method by [minPrec] minus [precision]. Otherwise we
     * initialize our `val result` to the [BigInteger] returned by our [approximate] method for
     * precision [precision], set our [minPrec] field to [precision], set our [maxAppr] field to
     * `result`, set our [apprValid] field to *true* and return result.
     *
     * @param precision number of bits of precision required.
     * @return A [BigInteger] which is an approximation of our value with [precision] bits of
     * precision scaled by 2 ** [precision].
     */
    @Synchronized
    open fun approxGet(precision: Int): BigInteger {
        checkPrec(precision)
        return if (apprValid && precision >= minPrec) {
            scale(maxAppr!!, minPrec - precision)
        } else {
            val result = approximate(precision)
            minPrec = precision
            maxAppr = result
            apprValid = true
            result
        }
    }

    /**
     * Return the position of the most significant digit.
     *
     * If x.msd() == n then 2**(n-1) < abs(x) < 2**(n+1)
     *
     * This initial version assumes that [maxAppr] is valid and sufficiently removed from zero
     * that the most significant digit is determinable.
     *
     * First we declare our `val firstDigit` to be an [Int], then we initialize our `val length` to
     * the [Int] bit length of our field [maxAppr] if [maxAppr] is greater than or equal to 0, or to
     * the bit length of the negative of [maxAppr] if it is negative. We then set `firstDigit` to
     * [minPrec] plus `length` minus 1 and return `firstDigit` to the caller.
     *
     * @return the position of the most significant digit.
     */
    internal fun knownMsd(): Int {
        val firstDigit: Int
        val length: Int = if (maxAppr!!.signum() >= 0) {
            maxAppr!!.bitLength()
        } else {
            maxAppr!!.negate().bitLength()
        }
        firstDigit = minPrec + length - 1
        return firstDigit
    }

    /**
     * Return the position of the most significant digit. This version may return Integer.MIN_VALUE
     * if the correct answer is < [n]. If our [apprValid] field is *false* (we do not have a valid
     * cached approximation yet) or [maxAppr] is less than or equal to the constant [big1]
     * ([BigInteger.ONE]) and greater than or equal to the constant [bigm1] (the [BigInteger] of -1)
     * we call our [approxGet] method for a precision of [n] minus 1, and if the absolute value of
     * cached value it calculates for our field [maxAppr] is less than or equal to the constant [big1]
     * we return [Integer.MIN_VALUE] since the most significant digit could still be arbitrarily far
     * to the right. Otherwise we now know we have a valid cached approximation so we return the
     * value returned by our [knownMsd] method to the caller.
     *
     * @param n the precision of the approximation we are to use when we search for the most
     * significant digit.
     * @return the position of the most significant digit.
     */
    internal fun msd(n: Int): Int {
        @Suppress("ReplaceCallWithBinaryOperator")
        if (!apprValid || maxAppr!!.compareTo(big1) <= 0 && maxAppr!!.compareTo(bigm1) >= 0) {
            approxGet(n - 1)
            if (maxAppr!!.abs().compareTo(big1) <= 0) {
                // msd could still be arbitrarily far to the right.
                return Integer.MIN_VALUE
            }
        }
        return knownMsd()
    }

    /**
     * Return the position of the most significant digit. Functionally equivalent to our [msd] method,
     * but iteratively evaluates to higher precision. First we initialize our [Int] `var prec` to 0.
     * Then we loop while `prec` is greater than [n] plus 30:
     *  - We initialize our `val msd` to the [Int] returned by our method [msd] for a precision of
     *  `prec`.
     *  - If `msd` is not equal to [Integer.MIN_VALUE] we return `msd` to the caller.
     *  - Otherwise we call our method [checkPrec] to have it check that `prec` is at least a factor
     *  of 8 away from overflowing the integer used to hold a precision spec (it throws the exception
     *  [PrecisionOverflowException] if it is not)
     *  - We then check whether our thread has been interrupted or our [pleaseStop] field set to
     *  *true* in which case we throw the exception [AbortedException].
     *  - We now want to try with a higher precision so we multiply `prec` by three halves and
     *  subtract 16 then loop around to try again.
     *
     * If our loop fails to find the most significant digit we return the value returned by our
     * method [msd] for a precision of [n] to the caller.
     *
     * @param n the precision of the approximation we are to use when we iteratively search for the
     * most significant digit.
     * @return the position of the most significant digit.
     */
    internal fun iterMsd(n: Int): Int {
        var prec = 0

        while (prec > n + 30) {
            val msd = msd(prec)
            if (msd != Integer.MIN_VALUE) return msd
            checkPrec(prec)
            if (Thread.interrupted() || pleaseStop) {
                throw AbortedException()
            }
            prec = prec * 3 / 2 - 16
        }
        return msd(n)
    }

    /**
     * Return the position of the most significant digit. This version returns a correct answer
     * eventually, except that it loops forever (or throws an exception when the requested
     * precision used by [iterMsd] overflows) if this constructive real is zero. We just return
     * the value returned by our our [iterMsd] method for a precision of [Integer.MIN_VALUE].
     *
     * @return the position of the most significant digit.
     */
    internal fun msd(): Int {
        return iterMsd(Integer.MIN_VALUE)
    }

    /**
     * Natural log of 2. Needed for some pre-scaling below.
     *
     * ln(2) = 7ln(10/9) - 2ln(25/24) + 3ln(81/80)
     *
     * @return a [PrescaledLnCR] instance constructed from *this* minus the [CR] constant [ONE].
     */
    internal fun simpleLn(): CR {
        return PrescaledLnCR(this.subtract(ONE))
    }

    // Public operations.

    /**
     * Return 0 if *this* = [x] to within the indicated tolerance, -1 if *this* < [x], and +1 if
     * *this* > [x].  If [x] and *this* are indeed equal, it is guaranteed that 0 will be returned.
     * If they differ by less than the tolerance, anything may happen. The tolerance allowed is the
     * maximum of (abs(*this*)+abs([x]))*(2**[r]) and 2**[a]. We initialize our `val thisMsd` to the
     * [Int] location of the most significant digit or *this* returned by our [iterMsd] method for a
     * precision of [a]. We initialize our `val xMsd` to the [Int] location of the most significant
     * digit or [x] returned by our [iterMsd] method for a precision of `thisMsd` if `thisMsd` is
     * greater than [a] or a precision of [a] if it is not. We initialize our `val maxMsd` to the
     * larger of `xMsd` and `thisMsd`. If `maxMsd` is equal to [Integer.MIN_VALUE] we return 0 ([x]
     * and *this* are equal). Otherwise we call our method [checkPrec] to have it check that `r` is
     * at least a factor of 8 away from overflowing the integer used to hold a precision spec (it
     * throws the exception [PrecisionOverflowException] if it is not). If we pass this check we
     * then initialize our `val rel` to `maxMsd` plus [r], and our `val absPrec` to `rel` if `rel`
     * is greater than [a] or to [a] is it is not. Finally we return the value returned by our
     * approximate [compareTo] method when it compares [x] to *this* for an absolute tolerance of
     * `absPrec`,
     *
     * @param x The other constructive real
     * @param r Relative tolerance in bits
     * @param a Absolute tolerance in bits
     * @return 0 if *this* = [x] to within the indicated tolerance, -1 if *this* < [x], and +1 if
     * *this* > [x].
     */
    fun compareTo(x: CR, r: Int, a: Int): Int {
        val thisMsd = iterMsd(a)
        val xMsd = x.iterMsd(if (thisMsd > a) thisMsd else a)
        val maxMsd = if (xMsd > thisMsd) xMsd else thisMsd
        if (maxMsd == Integer.MIN_VALUE) {
            return 0
        }
        checkPrec(r)
        val rel = maxMsd + r
        val absPrec = if (rel > a) rel else a
        return compareTo(x, absPrec)
    }

    /**
     * Approximate comparison with only an absolute tolerance. Identical to the three argument
     * version, but without a relative tolerance. Result is 0 if both constructive reals are equal,
     * indeterminate if they differ by less than 2**[a]. First we initialize our `val neededPrec` to
     * [a] minus 1, initialize `val thisAppr` to the [BigInteger] returned by our [approxGet] method
     * for a precision of `neededPrec`, and initialize `val xAppr` to the [BigInteger] returned by
     * the [approxGet] method of [x]. We then initialize `val comp1` to the [Int] returned when the
     * [compareTo] method of `thisAppr` compares itself to the quantity `xAppr` plus the constant
     * [big1] (one). If `comp1` is greater than 0 we return 1. Otherwise we initialize our `val comp2`
     * to the [Int] returned when the [compareTo] method of `thisAppr` compares itself to the quantity
     * `xAppr` minus the constant [big1]. If `comp2` is less than 0 we return -1, otherwise we return
     * 0.
     *
     * @param x The other constructive real
     * @param a Absolute tolerance in bits
     * @return 0 if *this* = [x] to within the indicated tolerance, -1 if *this* < [x], and +1 if
     * *this* > [x].
     */
    fun compareTo(x: CR, a: Int): Int {
        val neededPrec = a - 1
        val thisAppr = approxGet(neededPrec)
        val xAppr = x.approxGet(neededPrec)
        val comp1 = thisAppr.compareTo(xAppr.add(big1))
        if (comp1 > 0) return 1
        val comp2 = thisAppr.compareTo(xAppr.subtract(big1))
        return if (comp2 < 0) -1 else 0
    }

    /**
     * Return -1 if *this* < [x], or +1 if *this* > [x]. Should be called only if *this* != [x].
     * If *this* == [x], this will not terminate correctly; typically it will run until it exhausts
     * memory. If the two constructive reals may be equal, the two or 3 argument version of [compareTo]
     * should be used. First we initialize our `var a` to -20. Then we loop "forever":
     *  - We call our method [checkPrec] to have it check that `a` is at least a factor of 8 away
     *  from overflowing the integer used to hold a precision spec (it throws the exception
     *  [PrecisionOverflowException] if it is not).
     *  - If we pass this check we initialize our `val result` to the [Int] returned by our two
     *  argument [compareTo] method when it compares *this* to [x] for a tolerance of `a`.
     *  - If `result` is not 0 we return `result` to the caller.
     *  - Otherwise we check to see if our thread was interrupted or our [pleaseStop] variable was
     *  set to *true* and if so we throw [AbortedException].
     *  - If we are clear to keep running we multiply `a` by 2 and loop around to try again.
     *
     * @param x The other constructive real
     * @return -1 if *this* < [x], and +1 if *this* > [x]. Loops until overflow is *this* = [x].
     */
    operator fun compareTo(x: CR): Int {
        var a = -20
        while (true) {
            checkPrec(a)
            val result = compareTo(x, a)
            if (0 != result) return result
            if (Thread.interrupted() || pleaseStop) {
                throw AbortedException()
            }
            a *= 2
        }
    }

    /**
     * Equivalent to a call to the [compareTo] method to compare *this* to a [CR] constructed to hold
     * 0 with a tolerance of [a]. If our [apprValid] field is *true* (indicating we have a valid
     * cached approximation) we initialize our `val quickTry` to the [Int] returned by the `signum`
     * method of our cached approximation [maxAppr]. If `quickTry` is not equal to 0 we return it to
     * the caller. Otherwise we initialize our `val neededPrec` to [a] minus 1, and initialize our
     * `val thisAppr` to the [BigInteger] approximation returned by our [approxGet] to a precision
     * of `neededPrec`. Then we return the value returned by the `signum` method of `thisAppr` to
     * the caller.
     *
     * @param a Absolute tolerance in bits
     * @return 0 if *this* = 0 to within the indicated tolerance, -1 if *this* < 0, and +1 if
     * *this* > 0.
     */
    fun signum(a: Int): Int {
        if (apprValid) {
            val quickTry = maxAppr!!.signum()
            if (0 != quickTry) return quickTry
        }
        val neededPrec = a - 1
        val thisAppr = approxGet(neededPrec)
        return thisAppr.signum()
    }

    /**
     * Return -1 if *this* is negative, +1 if it is positive. Should be called only if *this* != 0.
     * In the 0 case, this will not terminate correctly; typically it will run until it exhausts
     * memory. If the two constructive reals may be equal, the one or two argument version of `signum`
     * should be used. First we initialize our `var a` to -20. Then we loop "forever":
     *  - We call our method [checkPrec] to have it check that `a` is at least a factor of 8 away
     *  from overflowing the integer used to hold a precision spec (it throws the exception
     *  [PrecisionOverflowException] if it is not).
     *  - If we pass this check we initialize our `val result` to the [Int] returned by our `signum`
     *  method when it compares *this* to 0 for a tolerance of `a`.
     *  - If `result` is not 0 we return `result` to the caller.
     *  - Otherwise we check to see if our thread was interrupted or our [pleaseStop] variable was
     *  set to *true* and if so we throw [AbortedException].
     *  - If we are clear to keep running we multiply `a` by 2 and loop around to try again.
     *
     * @return -1 if *this* is negative, +1 if it is positive.
     */
    fun signum(): Int {
        var a = -20
        while (true) {
            checkPrec(a)
            val result = signum(a)
            if (0 != result) return result
            if (Thread.interrupted() || pleaseStop) {
                throw AbortedException()
            }
            a *= 2
        }
    }

    /**
     * Return a textual representation accurate to [n] places to the right of the decimal point.
     * [n] must be non-negative. First we declare our `val scaledCR` to be a [CR]. Then if [radix]
     * is equal to 16 we set `scaledCR` to a [CR] constructed from *this* shifted left by 4 times
     * [n], if it is not equal to 16 we initialize our `val scaleFactor` to a [BigInteger] which
     * is [radix] raised to the power [n] and set `scaledCR` to *this* times a [CR] constructed
     * from `scaleFactor`.
     *
     * Having calculated a `scaledCR` from *this* we initialize our `val scaledInt` to the [BigInteger]
     * approximation of `scaledCR` for a precision of 0, and initialize our `var scaledString` to
     * the string value in radix [radix] of the absolute value of `scaledInt`. We then declare our
     * `var result` to be a [String]. If [n] is 0 (no digits to the right of decimal point needed)
     * we just set `result` to `scaledString`, otherwise we initialize our `var len` to the length
     * of `scaledString`. If `len` is less than or equal to [n] we need to add leading zeroes so we
     * initialize our `val z` to the [String] of [n] plus 1 minus `len` returned by our [zeroes]
     * method, prepend `z` to `scaledString` and set `len` to [n] plus 1.
     *
     * Next we initialize our `val whole` to the substring of `scaledString` from index 0 to index
     * `len` minus [n], and initialize our `val fraction` to the rest of `scaledString` from index
     * `len` minus [n] to its end. We then set `result` to the formed by concatenating `whole`
     * followed a decimal point followed by `fraction`.
     *
     * If `scaledInt` is negative we prepend a minus sign to `result`. Finally we return `result`
     * to the caller.
     *
     * @param n     Number of digits (>= 0) included to the right of decimal point
     * @param radix Base ( >= 2, <= 16) for the resulting representation.
     * @return a textual representation of *this* in radix [radix] accurate to [n] places to the
     * right of the decimal point.
     */
    @JvmOverloads
    fun toString(n: Int, radix: Int = 10): String {
        val scaledCR: CR
        scaledCR = if (16 == radix) {
            shiftLeft(4 * n)
        } else {
            val scaleFactor = BigInteger.valueOf(radix.toLong()).pow(n)
            multiply(IntCR(scaleFactor))
        }
        val scaledInt = scaledCR.approxGet(0)
        var scaledString = scaledInt.abs().toString(radix)
        var result: String
        if (0 == n) {
            result = scaledString
        } else {
            var len = scaledString.length
            if (len <= n) {
                // Add sufficient leading zeroes
                val z = zeroes(n + 1 - len)
                scaledString = z + scaledString
                len = n + 1
            }
            val whole = scaledString.substring(0, len - n)
            val fraction = scaledString.substring(len - n)
            result = "$whole.$fraction"
        }
        if (scaledInt.signum() < 0) {
            result = "-$result"
        }
        return result
    }

    /**
     * Equivalent to `toString(10, 10)`
     *
     * @return a textual representation of *this* in radix 10 accurate to 10 places to the
     * right of the decimal point.
     */
    override fun toString(): String {
        return toString(10)
    }

    /**
     * Return a textual scientific notation representation accurate to [n] places to the right of
     * the decimal point. [n] must be non-negative. A value smaller than [radix]**-[m] may be
     * displayed as 0. The _mantissa_ component of the result is either "0" or exactly [n] digits
     * long.  The _sign_ component is zero exactly when the _mantissa_ is "0". If [n] is less than
     * or equal to 0 we throw an [ArithmeticException] "Bad precision argument". Otherwise we
     * initialize our `val log2Radix` to the natural log of [radix] divided by the constant
     * [doubleLog2] (this is the log base 2 of [radix]). We initialize our `val bigRadix` to a
     * [BigInteger] constructed from [radix], and initialize our `val longMsdPrec` to the truncated
     * [Long] value of `log2Radix` times [m]. If `longMsdPrec` is greater than [Integer.MAX_VALUE]
     * or less than [Integer.MIN_VALUE] we throw a [PrecisionOverflowException]. Otherwise we
     * initialize our `val msdPrec` to the [Int] value of `longMsdPrec`. We then call our method
     * [checkPrec] to have it check that `msdPrec` is at least a factor of 8 away from overflowing
     * the integer used to hold a precision spec (it throws the exception [PrecisionOverflowException]
     * if it is not). If we pass this check we initialize our `val msd` to the [Int] location of
     * the most significant digit of *this* that our [iterMsd] finds for a precision of `msdPrec`
     * minus 2. If `msd` is equal to [Integer.MIN_VALUE] (the most significant digit is arbitrarily
     * far to the right) we return a [StringFloatRep] constructed with a sign of 0, a mantissa of
     * "0", a radix of [radix] and an exponent of 0. Otherwise we initialize our `var exponent` to
     * the rounded up [Int] value of `msd` divided by `log2Radix`, and our `val scaleExp` to `exponent`
     * minus [n].
     *
     * @param n     Number of digits (> 0) included to the right of decimal point.
     * @param radix Base (  2,  16) for the resulting representation.
     * @param m     Precision used to distinguish number from zero. Expressed as a power of [m].
     * @return      A textual scientific notation representation in radix [radix] accurate to [n]
     * places to the right of the decimal point.
     */
    fun toStringFloatRep(n: Int, radix: Int, m: Int): StringFloatRep {
        if (n <= 0) throw ArithmeticException("Bad precision argument")
        val log2Radix = ln(radix.toDouble()) / doubleLog2
        val bigRadix = BigInteger.valueOf(radix.toLong())
        val longMsdPrec = (log2Radix * m.toDouble()).toLong()
        if (longMsdPrec > Integer.MAX_VALUE.toLong() || longMsdPrec < Integer.MIN_VALUE.toLong())
            throw PrecisionOverflowException()
        val msdPrec = longMsdPrec.toInt()
        checkPrec(msdPrec)
        val msd = iterMsd(msdPrec - 2)
        if (msd == Integer.MIN_VALUE)
            return StringFloatRep(0, "0", radix, 0)
        var exponent = ceil(msd.toDouble() / log2Radix).toInt()
        // Guess for the exponent.  Try to get it usually right.
        val scaleExp = exponent - n
        val scale: CR
        scale = if (scaleExp > 0) {
            valueOf(bigRadix.pow(scaleExp)).inverse()
        } else {
            valueOf(bigRadix.pow(-scaleExp))
        }
        var scaledRes = multiply(scale)
        var scaledInt = scaledRes.approxGet(0)
        var sign = scaledInt.signum()
        var scaledString = scaledInt.abs().toString(radix)
        while (scaledString.length < n) {
            // exponent was too large.  Adjust.
            scaledRes = scaledRes.multiply(valueOf(bigRadix))
            exponent -= 1
            scaledInt = scaledRes.approxGet(0)
            sign = scaledInt.signum()
            scaledString = scaledInt.abs().toString(radix)
        }
        if (scaledString.length > n) {
            // exponent was too small.  Adjust by truncating.
            exponent += scaledString.length - n
            scaledString = scaledString.substring(0, n)
        }
        return StringFloatRep(sign, scaledString, radix, exponent)
    }

    /**
     * Return a BigInteger which differs by less than one from the
     * constructive real.
     */
    fun bigIntegerValue(): BigInteger {
        return approxGet(0)
    }

    /**
     * Return an int which differs by less than one from the
     * constructive real.  Behavior on overflow is undefined.
     */
    override fun intValue(): Int {
        return bigIntegerValue().toInt()
    }

    /**
     * Return an int which differs by less than one from the
     * constructive real.  Behavior on overflow is undefined.
     */
    override fun byteValue(): Byte {
        return bigIntegerValue().toByte()
    }

    /**
     * Return a long which differs by less than one from the
     * constructive real.  Behavior on overflow is undefined.
     */
    override fun longValue(): Long {
        return bigIntegerValue().toLong()
    }

    /**
     * Return a double which differs by less than one in the least
     * represented bit from the constructive real.
     * (We're in fact closer to round-to-nearest than that, but we can't and
     * don't promise correct rounding.)
     */
    override fun doubleValue(): Double {
        val myMsd = iterMsd(-1080 /* slightly > exp. range */)
        if (Integer.MIN_VALUE == myMsd) return 0.0
        val neededPrec = myMsd - 60
        val scaledInt = approxGet(neededPrec).toDouble()
        val mayUnderflow = neededPrec < -1000
        var scaledIntRep = java.lang.Double.doubleToLongBits(scaledInt)
        val expAdj = (if (mayUnderflow) neededPrec + 96 else neededPrec).toLong()
        val origExp = scaledIntRep shr 52 and 0x7ff
        if (origExp + expAdj and 0x7ff.inv() != 0L) {
            // Original unbiased exponent is > 50. Exp_adj > -1050.
            // Thus this can overflow the 11 bit exponent only if the result
            // itself overflows.
            return if (scaledInt < 0.0) {
                java.lang.Double.NEGATIVE_INFINITY
            } else {
                java.lang.Double.POSITIVE_INFINITY
            }
        }
        scaledIntRep += expAdj shl 52
        val result = java.lang.Double.longBitsToDouble(scaledIntRep)
        return if (mayUnderflow) {
            val two48 = (1L shl 48).toDouble()
            result / two48 / two48
        } else {
            result
        }
    }

    /**
     * Return a float which differs by less than one in the least
     * represented bit from the constructive real.
     */
    override fun floatValue(): Float {
        return doubleValue().toFloat()
        // Note that double-rounding is not a problem here, since we
        // cannot, and do not, guarantee correct rounding.
    }

    /**
     * Add two constructive reals.
     */
    fun add(x: CR): CR {

        return AddCR(this, x)
    }

    /**
     * Multiply a constructive real by 2**n.
     *
     * @param n shift count, may be negative
     */
    fun shiftLeft(n: Int): CR {
        checkPrec(n)
        return ShiftedCR(this, n)
    }

    /**
     * Multiply a constructive real by 2**(-n).
     *
     * @param n shift count, may be negative
     */
    fun shiftRight(n: Int): CR {
        checkPrec(n)
        return ShiftedCR(this, -n)
    }

    /**
     * Produce a constructive real equivalent to the original, assuming
     * the original was an integer.  Undefined results if the original
     * was not an integer.  Prevents evaluation of digits to the right
     * of the decimal point, and may thus improve performance.
     */
    fun assumeInt(): CR {
        return AssumedIntCR(this)
    }

    /**
     * The additive inverse of a constructive real
     */
    fun negate(): CR {
        return NegCR(this)
    }

    /**
     * The difference between two constructive reals
     */
    fun subtract(x: CR): CR {
        return AddCR(this, x.negate())
    }

    /**
     * The product of two constructive reals
     */
    fun multiply(x: CR): CR {

        return MultCR(this, x)
    }

    /**
     * The multiplicative inverse of a constructive real.
     * <TT>x.inverse()</TT> is equivalent to <TT>CR.valueOf(1).divide(x)</TT>.
     */
    fun inverse(): CR {
        return InvCR(this)
    }

    /**
     * The quotient of two constructive reals.
     */
    fun divide(x: CR): CR {
        return MultCR(this, x.inverse())
    }

    /**
     * The real number <TT>x</TT> if <TT>this</TT> < 0, or <TT>y</TT> otherwise.
     * Requires <TT>x</TT> = <TT>y</TT> if <TT>this</TT> = 0.
     * Since comparisons may diverge, this is often
     * a useful alternative to conditionals.
     */
    fun select(x: CR, y: CR): CR {
        return SelectCR(this, x, y)
    }

    /**
     * The maximum of two constructive reals.
     */
    fun max(x: CR): CR {
        return subtract(x).select(x, this)
    }

    /**
     * The minimum of two constructive reals.
     */
    fun min(x: CR): CR {

        return subtract(x).select(this, x)
    }

    /**
     * The absolute value of a constructive reals.
     * Note that this cannot be written as a conditional.
     */
    fun abs(): CR {
        return select(negate(), this)
    }

    /**
     * The exponential function, that is e**<TT>this</TT>.
     */
    fun exp(): CR {
        val lowPrec = -10
        val roughAppr = approxGet(lowPrec)
        // Handle negative arguments directly; negating and computing inverse
        // can be very expensive.
        @Suppress("ReplaceCallWithBinaryOperator")
        return if (roughAppr.compareTo(big2) > 0 || roughAppr.compareTo(bigm2) < 0) {
            val squareRoot = shiftRight(1).exp()
            squareRoot.multiply(squareRoot)
        } else {
            PrescaledExpCR(this)
        }
    }

    /**
     * The trigonometric cosine function.
     */
    fun cos(): CR {
        val halfpiMultiples = divide(PI).approxGet(-1)
        val absHalfpiMultiples = halfpiMultiples.abs()
        @Suppress("ReplaceCallWithBinaryOperator")
        when {
            absHalfpiMultiples.compareTo(big2) >= 0 -> {
                // Subtract multiples of PI
                val piMultiples = scale(halfpiMultiples, -1)
                val adjustment = PI.multiply(valueOf(piMultiples))
                return if (piMultiples.and(big1).signum() != 0) {
                    subtract(adjustment).cos().negate()
                } else {
                    subtract(adjustment).cos()
                }
            }
            approxGet(-1).abs().compareTo(big2) >= 0 -> {
                // Scale further with double angle formula
                val cosHalf = shiftRight(1).cos()
                return cosHalf.multiply(cosHalf).shiftLeft(1).subtract(ONE)
            }
            else -> return PrescaledCosCR(this)
        }
    }

    /**
     * The trigonometric sine function.
     */
    fun sin(): CR {
        return halfPi.subtract(this).cos()
    }

    /**
     * The trigonometric arc (inverse) sine function.
     */
    fun asin(): CR {
        val roughAppr = approxGet(-10)
        @Suppress("ReplaceCallWithBinaryOperator")
        return when {
            roughAppr.compareTo(big750) /* 1/sqrt(2) + a bit */ > 0 -> {
                val newArg = ONE.subtract(multiply(this)).sqrt()
                newArg.acos()
            }
            roughAppr.compareTo(bigm750) < 0 -> negate().asin().negate()
            else -> PrescaledAsinCR(this)
        }
    }

    /**
     * The trigonometric arc (inverse) cosine function.
     */
    fun acos(): CR {
        return halfPi.subtract(asin())
    }

    /**
     * The natural (base e) logarithm.
     */
    @Suppress("ReplaceCallWithBinaryOperator")
    fun ln(): CR {
        val lowPrec = -4
        val roughAppr = approxGet(lowPrec) /* In sixteenths */
        if (roughAppr.compareTo(big0) < 0) {
            throw ArithmeticException("ln(negative)")
        }
        if (roughAppr.compareTo(LOW_LN_LIMIT) <= 0) {
            return inverse().ln().negate()
        }
        if (roughAppr.compareTo(HIGH_LN_LIMIT) >= 0) {
            return if (roughAppr.compareTo(SCALED_4) <= 0) {
                val quarter = sqrt().sqrt().ln()
                quarter.shiftLeft(2)
            } else {
                val extraBits = roughAppr.bitLength() - 3
                val scaledResult = shiftRight(extraBits).ln()
                scaledResult.add(valueOf(extraBits).multiply(ln2))
            }
        }
        return simpleLn()
    }

    /**
     * The square root of a constructive real.
     */
    fun sqrt(): CR {
        return SqrtCR(this)
    }

    companion object {

        // First some frequently used constants, so we don't have to
        // recompute these all over the place.
        internal val big0 = BigInteger.ZERO
        internal val big1 = BigInteger.ONE
        internal val bigm1 = BigInteger.valueOf(-1)
        internal val big2 = BigInteger.valueOf(2)
        internal val bigm2 = BigInteger.valueOf(-2)
        @Suppress("unused")
        internal val big3 = BigInteger.valueOf(3)
        internal val big6 = BigInteger.valueOf(6)
        internal val big8 = BigInteger.valueOf(8)
        @Suppress("unused")
        internal val big10 = BigInteger.TEN
        internal val big750 = BigInteger.valueOf(750)
        internal val bigm750 = BigInteger.valueOf(-750)

        /**
         * Setting this to true requests that  all computations be aborted by
         * throwing AbortedException.  Must be reset to false before any further
         * computation.  Ideally Thread.interrupt() should be used instead, but
         * that doesn't appear to be consistently supported by browser VMs.
         */
        @Volatile
        var pleaseStop = false

        // Helper functions
        internal fun boundLog2(n: Int): Int {
            val absN = abs(n)
            return ceil(ln((absN + 1).toDouble()) / ln(2.0)).toInt()
        }

        // Check that a precision is at least a factor of 8 away from
        // overflowing the integer used to hold a precision spec.
        // We generally perform this check early on, and then convince
        // ourselves that none of the operations performed on precisions
        // inside a function can generate an overflow.
        internal fun checkPrec(n: Int) {
            val high = n shr 28
            // if n is not in danger of overflowing, then the 4 high order
            // bits should be identical.  Thus high is either 0 or -1.
            // The rest of this is to test for either of those in a way
            // that should be as cheap as possible.
            val highShifted = n shr 29
            if (0 != high xor highShifted) {
                throw PrecisionOverflowException()
            }
        }

        /**
         * The constructive real number corresponding to a
         * <TT>BigInteger</TT>.
         */
        fun valueOf(n: BigInteger): CR {
            return IntCR(n)
        }

        /**
         * The constructive real number corresponding to a
         * Java <TT>int</TT>.
         */
        fun valueOf(n: Int): CR {
            return valueOf(BigInteger.valueOf(n.toLong()))
        }

        /**
         * The constructive real number corresponding to a
         * Java <TT>long</TT>.
         */
        fun valueOf(n: Long): CR {
            return valueOf(BigInteger.valueOf(n))
        }

        /**
         * The constructive real number corresponding to a
         * Java <TT>double</TT>.
         * The result is undefined if argument is infinite or NaN.
         */
        fun valueOf(n: Double): CR {
            if (java.lang.Double.isNaN(n)) throw ArithmeticException("Nan argument")
            if (java.lang.Double.isInfinite(n)) {
                throw ArithmeticException("Infinite argument")
            }
            val negative = n < 0.0
            val bits = java.lang.Double.doubleToLongBits(abs(n))
            var mantissa = bits and 0xfffffffffffffL
            val biasedExp = (bits shr 52).toInt()
            val exp = biasedExp - 1075
            if (biasedExp != 0) {
                mantissa += 1L shl 52
            } else {
                mantissa = mantissa shl 1
            }
            var result = valueOf(mantissa).shiftLeft(exp)
            if (negative) result = result.negate()
            return result
        }

        /**
         * The constructive real number corresponding to a
         * Java <TT>float</TT>.
         * The result is undefined if argument is infinite or NaN.
         */
        @Suppress("unused")
        fun valueOf(n: Float): CR {
            return valueOf(n.toDouble())
        }

        @Suppress("unused")
        var ZERO = valueOf(0)
        var ONE = valueOf(1)

        // Multiply k by 2**n.
        internal fun shift(k: BigInteger, n: Int): BigInteger {
            if (n == 0) return k
            return if (n < 0) k.shiftRight(-n) else k.shiftLeft(n)
        }

        // Multiply by 2**n, rounding result
        internal fun scale(k: BigInteger, n: Int): BigInteger {
            return if (n >= 0) {
                k.shiftLeft(n)
            } else {
                val adjK = shift(k, n + 1).add(big1)
                adjK.shiftRight(1)
            }
        }

        // A helper function for toString.
        // Generate a String containing n zeroes.
        private fun zeroes(n: Int): String {
            val a = CharArray(n)
            for (i in 0 until n) {
                a[i] = '0'
            }
            return String(a)
        }

        internal var tenNinths = valueOf(10).divide(valueOf(9))
        internal var twentyfiveTwentyfourths = valueOf(25).divide(valueOf(24))
        internal var eightyoneEightyeths = valueOf(81).divide(valueOf(80))
        internal var ln2s1 = valueOf(7).multiply(tenNinths.simpleLn())
        internal var ln2s2 = valueOf(2).multiply(twentyfiveTwentyfourths.simpleLn())
        internal var ln2s3 = valueOf(3).multiply(eightyoneEightyeths.simpleLn())
        internal var ln2 = ln2s1.subtract(ln2s2).add(ln2s3)

        // Atan of integer reciprocal.  Used for atanPI.  Could perhaps be made
        // public.
        internal fun atanReciprocal(n: Int): CR {
            return IntegralAtanCR(n)
        }

        // Other constants used for PI computation.
        internal var four = valueOf(4)

        /**
         * Return the constructive real number corresponding to the given
         * textual representation and radix.
         *
         * @param s     [-] digit* [. digit*]
         * @param radix radix of number in our string parameter
         */

        @Suppress("unused")
        @Throws(NumberFormatException::class)
        fun valueOf(s: String, radix: Int): CR {
            var len = s.length
            var startPos = 0
            var pointPos: Int
            val fraction: String
            while (s[startPos] == ' ') ++startPos
            while (s[len - 1] == ' ') --len
            pointPos = s.indexOf('.', startPos)
            if (pointPos == -1) {
                pointPos = len
                fraction = "0"
            } else {
                fraction = s.substring(pointPos + 1, len)
            }
            val whole = s.substring(startPos, pointPos)
            val scaledResult = BigInteger(whole + fraction, radix)
            val divisor = BigInteger.valueOf(radix.toLong()).pow(fraction.length)
            return valueOf(scaledResult).divide(valueOf(divisor))
        }

        internal var doubleLog2 = ln(2.0)

        /**
         * The ratio of a circle's circumference to its diameter.
         */
        var PI: CR = GlPiCR()

        // Our old PI implementation. Keep this around for now to allow checking.
        // This implementation may also be faster for BigInteger implementations
        // that support only quadratic multiplication, but exhibit high performance
        // for small computations.  (The standard Android 6 implementation supports
        // sub-quadratic multiplication, but has high constant overhead.) Many other
        // atan-based formulas are possible, but based on superficial
        // experimentation, this is roughly as good as the more complex formulas.
        @Suppress("unused")
        var atanPI = four.multiply(four.multiply(atanReciprocal(5))
                .subtract(atanReciprocal(239)))
        // pi/4 = 4*atan(1/5) - atan(1/239)
        internal var halfPi = PI.shiftRight(1)

        internal val LOW_LN_LIMIT = big8 /* sixteenths, i.e. 1/2 */
        internal val HIGH_LN_LIMIT = BigInteger.valueOf((16 + 8).toLong() /* 1.5 */)
        internal val SCALED_4 = BigInteger.valueOf((4 * 16).toLong())
    }

}

// end of CR

/**
 * A specialization of CR for cases in which approximate() calls to increase evaluation precision
 * are somewhat expensive. If we need to (re)evaluate, we speculatively evaluate to slightly higher
 * precision, minimizing reevaluations.
 *
 * Note that this requires any arguments to be evaluated to higher precision than absolutely
 * necessary. It can thus potentially result in lots of wasted effort, and should be used
 * judiciously. This assumes that the order of magnitude of the number is roughly one.
 */
internal abstract class SlowCR : CR() {

    @Synchronized
    override fun approxGet(precision: Int): BigInteger {
        checkPrec(precision)
        return if (apprValid && precision >= minPrec) {
            scale(maxAppr!!, minPrec - precision)
        } else {

            val evalPrec = if (precision >= maxPrec)
                maxPrec
            else
                precision - precIncr + 1 and (precIncr - 1).inv()
            val result = approximate(evalPrec)
            minPrec = evalPrec
            maxAppr = result
            apprValid = true
            scale(result, evalPrec - precision)
        }
    }

    companion object {
        var maxPrec = -64
        var precIncr = 32
    }
}

/**
 * Representation of an integer constant. Private.
 */
internal class IntCR(var value: BigInteger) : CR() {

    override fun approximate(precision: Int): BigInteger {
        return scale(value, -precision)
    }
}

/**
 * Representation of a number that may not have been completely evaluated, but is assumed to be an
 * integer.  Hence we never evaluate beyond the decimal point.
 */
internal class AssumedIntCR(var value: CR) : CR() {

    override fun approximate(precision: Int): BigInteger {
        return if (precision >= 0) {
            value.approxGet(precision)
        } else {
            scale(value.approxGet(0), -precision)
        }
    }
}

/**
 * Representation of the sum of 2 constructive reals. Private.
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class AddCR(var op1: CR, var op2: CR) : CR() {

    override fun approximate(precision: Int): BigInteger {
        // Args need to be evaluated so that each error is < 1/4 ulp.
        // Rounding error from the cale call is <= 1/2 ulp, so that
        // final error is < 1 ulp.
        return scale(op1.approxGet(precision - 2).add(op2.approxGet(precision - 2)), -2)
    }
}

/**
 * Representation of a CR multiplied by 2**n
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class ShiftedCR(var op: CR, var count: Int) : CR() {

    override fun approximate(precision: Int): BigInteger {
        return op.approxGet(precision - count)
    }
}

/**
 * Representation of the negation of a constructive real. Private.
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class NegCR(var op: CR) : CR() {

    override fun approximate(precision: Int): BigInteger {
        return op.approxGet(precision).negate()
    }
}

/**
 * Representation of: op1 if selector < 0, op2 if selector >= 0. Assumes x = y if s = 0
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class SelectCR(var selector: CR, var op1: CR, var op2: CR) : CR() {
    var selectorSign: Int = 0

    init {
        selectorSign = selector.approxGet(-20).signum()
    }

    override fun approximate(precision: Int): BigInteger {
        if (selectorSign < 0) return op1.approxGet(precision)
        if (selectorSign > 0) return op2.approxGet(precision)
        val op1Appr = op1.approxGet(precision - 1)
        val op2Appr = op2.approxGet(precision - 1)
        val diff = op1Appr.subtract(op2Appr).abs()
        @Suppress("ReplaceCallWithBinaryOperator")
        if (diff.compareTo(big1) <= 0) {
            // close enough; use either
            return scale(op1Appr, -1)
        }
        // op1 and op2 are different; selector != 0;
        // safe to get sign of selector.
        return if (selector.signum() < 0) {
            selectorSign = -1
            scale(op1Appr, -1)
        } else {
            selectorSign = 1
            scale(op2Appr, -1)
        }
    }
}

/**
 * Representation of the product of 2 constructive reals. Private.
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class MultCR(var op1: CR, var op2: CR) : CR() {

    override fun approximate(precision: Int): BigInteger {
        val halfPrec = (precision shr 1) - 1
        var msdOp1 = op1.msd(halfPrec)
        var msdOp2: Int

        if (msdOp1 == Integer.MIN_VALUE) {
            msdOp2 = op2.msd(halfPrec)
            if (msdOp2 == Integer.MIN_VALUE) {
                // Product is small enough that zero will do as an
                // approximation.
                return big0
            } else {
                // Swap them, so the larger operand (in absolute value)
                // is first.
                val tmp: CR = op1
                op1 = op2
                op2 = tmp
                msdOp1 = msdOp2
            }
        }
        // msdOp1 is valid at this point.
        val prec2 = precision - msdOp1 - 3    // Precision needed for op2.
        // The appr. error is multiplied by at most
        // 2 ** (msdOp1 + 1)
        // Thus each approximation contributes 1/4 ulp
        // to the rounding error, and the final rounding adds
        // another 1/2 ulp.
        val appr2 = op2.approxGet(prec2)
        if (appr2.signum() == 0) return big0
        msdOp2 = op2.knownMsd()
        val prec1 = precision - msdOp2 - 3    // Precision needed for op1.
        val appr1 = op1.approxGet(prec1)
        val scaleDigits = prec1 + prec2 - precision
        return scale(appr1.multiply(appr2), scaleDigits)
    }
}

/**
 * Representation of the multiplicative inverse of a constructive real. Private.
 * Should use Newton iteration to refine estimates.
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class InvCR(var op: CR) : CR() {

    override fun approximate(precision: Int): BigInteger {
        val msd = op.msd()
        val invMsd = 1 - msd
        val digitsNeeded = invMsd - precision + 3
        // Number of SIGNIFICANT digits needed for
        // argument, excl. msd position, which may
        // be fictitious, since msd routine can be
        // off by 1.  Roughly 1 extra digit is
        // needed since the relative error is the
        // same in the argument and result, but
        // this isn't quite the same as the number
        // of significant digits.  Another digit
        // is needed to compensate for slop in the
        // calculation.
        // One further bit is required, since the
        // final rounding introduces a 0.5 ulp
        // error.
        val precNeeded = msd - digitsNeeded
        val logScaleFactor = -precision - precNeeded
        if (logScaleFactor < 0) return big0
        val dividend = big1.shiftLeft(logScaleFactor)
        val scaledDivisor = op.approxGet(precNeeded)
        val absScaledDivisor = scaledDivisor.abs()
        val adjDividend = dividend.add(
                absScaledDivisor.shiftRight(1))
        // Adjustment so that final result is rounded.
        val result = adjDividend.divide(absScaledDivisor)
        return if (scaledDivisor.signum() < 0) {
            result.negate()
        } else {
            result
        }
    }
}

/**
 * Representation of the exponential of a constructive real. Private. Uses a Taylor series expansion.
 * Assumes |x| < 1/2.
 *
 *
 * Note: this is known to be a bad algorithm for floating point. Unfortunately, other alternatives
 * appear to require precomputed information.
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class PrescaledExpCR(var op: CR) : CR() {

    override fun approximate(precision: Int): BigInteger {
        if (precision >= 1) return big0
        val iterationsNeeded = -precision / 2 + 2  // conservative estimate > 0.
        //  Claim: each intermediate term is accurate
        //  to 2*2^calcPrecision.
        //  Total rounding error in series computation is
        //  2*iterationsNeeded*2^calcPrecision,
        //  exclusive of error in op.
        val calcPrecision = (precision - boundLog2(2 * iterationsNeeded)
                - 4) // for error in op, truncation.
        val opPrec = precision - 3
        val opAppr = op.approxGet(opPrec)
        // Error in argument results in error of < 3/8 ulp.
        // Sum of term eval. rounding error is < 1/16 ulp.
        // Series truncation error < 1/16 ulp.
        // Final rounding error is <= 1/2 ulp.
        // Thus final error is < 1 ulp.
        val scaled1 = big1.shiftLeft(-calcPrecision)
        var currentTerm = scaled1
        var currentSum = scaled1
        var n = 0
        val maxTruncError = big1.shiftLeft(precision - 4 - calcPrecision)
        @Suppress("ReplaceCallWithBinaryOperator")
        while (currentTerm.abs().compareTo(maxTruncError) >= 0) {
            if (Thread.interrupted() || pleaseStop) throw AbortedException()
            n += 1
            /* currentTerm = currentTerm * op / n */
            currentTerm = scale(currentTerm.multiply(opAppr), opPrec)
            currentTerm = currentTerm.divide(BigInteger.valueOf(n.toLong()))
            currentSum = currentSum.add(currentTerm)
        }
        return scale(currentSum, calcPrecision - precision)
    }
}

/**
 * Representation of the cosine of a constructive real. Private. Uses a Taylor series expansion.
 * Assumes |x| < 1.
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class PrescaledCosCR(var op: CR) : SlowCR() {

    override fun approximate(precision: Int): BigInteger {
        if (precision >= 1) return big0
        val iterationsNeeded = -precision / 2 + 4  // conservative estimate > 0.
        //  Claim: each intermediate term is accurate
        //  to 2*2^calcPrecision.
        //  Total rounding error in series computation is
        //  2*iterationsNeeded*2^calcPrecision,
        //  exclusive of error in op.
        val calcPrecision = precision - boundLog2(2 * iterationsNeeded) - 4 // for error in op, truncation.
        val opPrec = precision - 2
        val opAppr = op.approxGet(opPrec)
        // Error in argument results in error of < 1/4 ulp.
        // Cumulative arithmetic rounding error is < 1/16 ulp.
        // Series truncation error < 1/16 ulp.
        // Final rounding error is <= 1/2 ulp.
        // Thus final error is < 1 ulp.
        var currentTerm: BigInteger
        var n = 0
        val maxTruncError = big1.shiftLeft(precision - 4 - calcPrecision)
        currentTerm = big1.shiftLeft(-calcPrecision)
        var currentSum = currentTerm
        @Suppress("ReplaceCallWithBinaryOperator")
        while (currentTerm.abs().compareTo(maxTruncError) >= 0) {
            if (Thread.interrupted() || pleaseStop) throw AbortedException()
            n += 2
            /* currentTerm = - currentTerm * op * op / n * (n - 1)   */
            currentTerm = scale(currentTerm.multiply(opAppr), opPrec)
            currentTerm = scale(currentTerm.multiply(opAppr), opPrec)
            val divisor = BigInteger.valueOf((-n).toLong()).multiply(BigInteger.valueOf((n - 1).toLong()))
            currentTerm = currentTerm.divide(divisor)
            currentSum = currentSum.add(currentTerm)
        }
        return scale(currentSum, calcPrecision - precision)
    }
}

/**
 * The constructive real atan(1/n), where n is a small integer > base.
 * This gives a simple and moderately fast way to compute PI.
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class IntegralAtanCR(var op: Int) : SlowCR() {

    override fun approximate(precision: Int): BigInteger {
        if (precision >= 1) return big0
        val iterationsNeeded = -precision / 2 + 2  // conservative estimate > 0.
        //  Claim: each intermediate term is accurate
        //  to 2*base^calcPrecision.
        //  Total rounding error in series computation is
        //  2*iterationsNeeded*base^calcPrecision,
        //  exclusive of error in op.
        val calcPrecision = (precision - boundLog2(2 * iterationsNeeded)
                - 2) // for error in op, truncation.
        // Error in argument results in error of < 3/8 ulp.
        // Cumulative arithmetic rounding error is < 1/4 ulp.
        // Series truncation error < 1/4 ulp.
        // Final rounding error is <= 1/2 ulp.
        // Thus final error is < 1 ulp.
        val scaled1 = big1.shiftLeft(-calcPrecision)
        val bigOp = BigInteger.valueOf(op.toLong())
        val bigOpSquared = BigInteger.valueOf((op * op).toLong())
        val opInverse = scaled1.divide(bigOp)
        var currentPower = opInverse
        var currentTerm = opInverse
        var currentSum = opInverse
        var currentSign = 1
        var n = 1
        val maxTruncError = big1.shiftLeft(precision - 2 - calcPrecision)
        @Suppress("ReplaceCallWithBinaryOperator")
        while (currentTerm.abs().compareTo(maxTruncError) >= 0) {
            if (Thread.interrupted() || pleaseStop) throw AbortedException()
            n += 2
            currentPower = currentPower.divide(bigOpSquared)
            currentSign = -currentSign
            currentTerm = currentPower.divide(BigInteger.valueOf((currentSign * n).toLong()))
            currentSum = currentSum.add(currentTerm)
        }
        return scale(currentSum, calcPrecision - precision)
    }
}

/**
 * Representation for ln(1 + op)
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class PrescaledLnCR(var op: CR) : SlowCR() {

    // Compute an approximation of ln(1+x) to precision
    // prec. This assumes |x| < 1/2.
    // It uses a Taylor series expansion.
    // Unfortunately there appears to be no way to take
    // advantage of old information.
    // Note: this is known to be a bad algorithm for
    // floating point.  Unfortunately, other alternatives
    // appear to require precomputed tabular information.
    override fun approximate(precision: Int): BigInteger {
        if (precision >= 0) return big0
        val iterationsNeeded = -precision  // conservative estimate > 0.
        //  Claim: each intermediate term is accurate
        //  to 2*2^calcPrecision.  Total error is
        //  2*iterationsNeeded*2^calcPrecision
        //  exclusive of error in op.
        val calcPrecision = precision - boundLog2(2 * iterationsNeeded) - 4 // for error in op, truncation.
        val opPrec = precision - 3
        val opAppr = op.approxGet(opPrec)
        // Error analysis as for exponential.
        var xNth = scale(opAppr, opPrec - calcPrecision)
        var currentTerm = xNth  // x**n
        var currentSum = currentTerm
        var n = 1
        var currentSign = 1   // (-1)^(n-1)
        val maxTruncError = big1.shiftLeft(precision - 4 - calcPrecision)
        @Suppress("ReplaceCallWithBinaryOperator")
        while (currentTerm.abs().compareTo(maxTruncError) >= 0) {
            if (Thread.interrupted() || pleaseStop) throw AbortedException()
            n += 1
            currentSign = -currentSign
            xNth = scale(xNth.multiply(opAppr), opPrec)
            currentTerm = xNth.divide(BigInteger.valueOf((n * currentSign).toLong()))
            // x**n / (n * (-1)**(n-1))
            currentSum = currentSum.add(currentTerm)
        }
        return scale(currentSum, calcPrecision - precision)
    }
}

/**
 * Representation of the arcsine of a constructive real. Private. Uses a Taylor series expansion.
 * Assumes |x| < (1/2)^(1/3).
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class PrescaledAsinCR(var op: CR) : SlowCR() {

    override fun approximate(precision: Int): BigInteger {
        // The Taylor series is the sum of x^(2n+1) * (2n)!/(4^n n!^2 (2n+1))
        // Note that (2n)!/(4^n n!^2) is always less than one.
        // (The denominator is effectively 2n*2n*(2n-2)*(2n-2)*...*2*2
        // which is clearly > (2n)!)
        // Thus all terms are bounded by x^(2n+1).
        // Unfortunately, there's no easy way to prescale the argument
        // to less than 1/sqrt(2), and we can only approximate that.
        // Thus the worst case iteration count is fairly high.
        // But it doesn't make much difference.
        if (precision >= 2) return big0  // Never bigger than 4.
        val iterationsNeeded = -3 * precision / 2 + 4
        // conservative estimate > 0.
        // Follows from assumed bound on x and
        // the fact that only every other Taylor
        // Series term is present.
        //  Claim: each intermediate term is accurate
        //  to 2*2^calcPrecision.
        //  Total rounding error in series computation is
        //  2*iterationsNeeded*2^calcPrecision,
        //  exclusive of error in op.
        val calcPrecision = precision - boundLog2(2 * iterationsNeeded) - 4 // for error in op, truncation.
        val opPrec = precision - 3  // always <= -2
        val opAppr = op.approxGet(opPrec)
        // Error in argument results in error of < 1/4 ulp.
        // (Derivative is bounded by 2 in the specified range and we use
        // 3 extra digits.)
        // Ignoring the argument error, each term has an error of
        // < 3ulps relative to calcPrecision, which is more precise than p.
        // Cumulative arithmetic rounding error is < 3/16 ulp (relative to p).
        // Series truncation error < 2/16 ulp.  (Each computed term
        // is at most 2/3 of last one, so some of remaining series <
        // 3/2 * current term.)
        // Final rounding error is <= 1/2 ulp.
        // Thus final error is < 1 ulp (relative to p).
        val maxLastTerm = big1.shiftLeft(precision - 4 - calcPrecision)
        var exp = 1 // Current exponent, = 2n+1 in above expression
        var currentTerm = opAppr.shiftLeft(opPrec - calcPrecision)
        var currentSum = currentTerm
        var currentFactor = currentTerm
        // Current scaled Taylor series term
        // before division by the exponent.
        // Accurate to 3 ulp at calcPrecision.
        @Suppress("ReplaceCallWithBinaryOperator")
        while (currentTerm.abs().compareTo(maxLastTerm) >= 0) {
            if (Thread.interrupted() || pleaseStop) throw AbortedException()
            exp += 2
            // currentFactor = currentFactor * op * op * (exp-1) * (exp-2) /
            // (exp-1) * (exp-1), with the two exp-1 factors cancelling,
            // giving
            // currentFactor = currentFactor * op * op * (exp-2) / (exp-1)
            // Thus the error any in the previous term is multiplied by
            // op^2, adding an error of < (1/2)^(2/3) < 2/3 the original
            // error.
            currentFactor = currentFactor.multiply(BigInteger.valueOf((exp - 2).toLong()))
            currentFactor = scale(currentFactor.multiply(opAppr), opPrec + 2)
            // Carry 2 extra bits of precision forward; thus
            // this effectively introduces 1/8 ulp error.
            currentFactor = currentFactor.multiply(opAppr)
            val divisor = BigInteger.valueOf((exp - 1).toLong())
            currentFactor = currentFactor.divide(divisor)
            // Another 1/4 ulp error here.
            currentFactor = scale(currentFactor, opPrec - 2)
            // Remove extra 2 bits.  1/2 ulp rounding error.
            // currentFactor has original 3 ulp rounding error, which we
            // reduced by 1, plus < 1 ulp new rounding error.
            currentTerm = currentFactor.divide(BigInteger.valueOf(exp.toLong()))
            // Contributes 1 ulp error to sum plus at most 3 ulp
            // from currentFactor.
            currentSum = currentSum.add(currentTerm)
        }
        return scale(currentSum, calcPrecision - precision)
    }
}


@Suppress("MemberVisibilityCanBePrivate")
internal class SqrtCR : CR {
    var op: CR

    val fpPrec = 50     // Conservative estimate of number of
    // significant bits in double precision
    // computation.
    val fpOpPrec = 60

    constructor(x: CR) {
        op = x
    }

    // Explicitly provide an initial approximation.
    // Useful for arithmetic geometric mean algorithms, where we've previously
    // computed a very similar square root.
    constructor(x: CR, minP: Int, maxA: BigInteger) {
        op = x
        minPrec = minP
        maxAppr = maxA
        apprValid = true
    }

    override fun approximate(precision: Int): BigInteger {
        val maxOpPrecNeeded = 2 * precision - 1
        val msd = op.iterMsd(maxOpPrecNeeded)
        if (msd <= maxOpPrecNeeded) return big0
        val resultMsd = msd / 2                 // +- 1
        val resultDigits = resultMsd - precision     // +- 2
        if (resultDigits > fpPrec) {
            // Compute less precise approximation and use a Newton iter.
            val apprDigits = resultDigits / 2 + 6
            // This should be conservative.  Is fewer enough?
            val apprPrec = resultMsd - apprDigits
            val prodPrec = 2 * apprPrec
            // First compute the argument to maximal precision, so we don't end up
            // reevaluating it incrementally.
            val opAppr = op.approxGet(prodPrec)
            val lastAppr = approxGet(apprPrec)
            // Compute (lastAppr * lastAppr + opAppr) / lastAppr / 2
            // while adjusting the scaling to make everything work
            val prodPrecScaledNumerator = lastAppr.multiply(lastAppr).add(opAppr)
            val scaledNumerator = scale(prodPrecScaledNumerator, apprPrec - precision)
            val shiftedResult = scaledNumerator.divide(lastAppr)
            return shiftedResult.add(big1).shiftRight(1)
        } else {
            // Use a double precision floating point approximation.
            // Make sure all precisions are even
            val opPrec = msd - fpOpPrec and 1.inv()
            val workingPrec = opPrec - fpOpPrec
            val scaledBiAppr = op.approxGet(opPrec).shiftLeft(fpOpPrec)
            val scaledAppr = scaledBiAppr.toDouble()
            if (scaledAppr < 0.0)
                throw ArithmeticException("sqrt(negative)")
            val scaledFpSqrt = sqrt(scaledAppr)
            val scaledSqrt = BigInteger.valueOf(scaledFpSqrt.toLong())
            val shiftCount = workingPrec / 2 - precision
            return shift(scaledSqrt, shiftCount)
        }
    }
}

/**
 * The constant PI, computed using the Gauss-Legendre alternating arithmetic-geometric
 * mean algorithm:
 * a[0] = 1
 * b[0] = 1/sqrt(2)
 * t[0] = 1/4
 * p[0] = 1
 *
 * a[n+1] = (a[ n ] + b[ n ])/2        (arithmetic mean, between 0.8 and 1)
 * b[n+1] = sqrt(a[ n ] * b[ n ])      (geometric mean, between 0.7 and 1)
 * t[n+1] = t[ n ] - (2^n)(a[ n ]-a[n+1])^2,  (always between 0.2 and 0.25)
 *
 * pi is then approximated as (a[n+1]+b[n+1])^2 / 4*t[n+1].
 */
@Suppress("MemberVisibilityCanBePrivate")
internal class GlPiCR : SlowCR() {
    // In addition to the best approximation kept by the CR base class, we keep
    // the entire sequence b[n], to the extent we've needed it so far.  Each
    // reevaluation leads to slightly different sqrt arguments, but the
    // previous result can be used to avoid repeating low precision Newton
    // iterations for the sqrt approximation.
    var bPrec = ArrayList<Int?>()
    var bVal = ArrayList<BigInteger?>()

    init {
        bPrec.add(null)  // Zeroth entry unused.
        bVal.add(null)
    }

    override fun approximate(precision: Int): BigInteger {
        // Get us back into a consistent state if the last computation
        // was interrupted after pushing onto bPrec.
        if (bPrec.size > bVal.size) {
            bPrec.removeAt(bPrec.size - 1)
        }
        // Rough approximations are easy.
        if (precision >= 0) return scale(BigInteger.valueOf(3), -precision)
        // We need roughly log2(p) iterations.  Each iteration should
        // contribute no more than 2 ulps to the error in the corresponding
        // term (a[n], b[n], or t[n]).  Thus 2log2(n) bits plus a few for the
        // final calculation and rounding suffice.
        val extraEvalPrec = ceil(ln((-precision).toDouble()) / ln(2.0)).toInt() + 10
        // All our terms are implicitly scaled by evalPrec.
        val evalPrec = precision - extraEvalPrec
        var a = BigInteger.ONE.shiftLeft(-evalPrec)
        var b = SQRT_HALF.approxGet(evalPrec)
        var t = BigInteger.ONE.shiftLeft(-evalPrec - 2)
        var n = 0
        while (a.subtract(b).subtract(TOLERANCE).signum() > 0) {
            // Current values correspond to n, next* values to n + 1
            // bPrec.size() == bVal.size() >= n + 1
            val nextA = a.add(b).shiftRight(1)
            val nextB: BigInteger
            val aDiff = a.subtract(nextA)
            val bProd = a.multiply(b).shiftRight(-evalPrec)
            // We compute square root approximations using a nested
            // temporary CR computation, to avoid implementing BigInteger
            // square roots separately.
            val bProdAsCR = valueOf(bProd).shiftRight(-evalPrec)
            if (bPrec.size == n + 1) {
                // Add an n+1st slot.
                // Take care to make this exception-safe; bPrec and bVal
                // must remain consistent, even if we are interrupted, or run
                // out of memory. It's OK to just push on bPrec in that case.
                val nextBAsCR = bProdAsCR.sqrt()
                nextB = nextBAsCR.approxGet(evalPrec)
                val scaledNextB = scale(nextB, -extraEvalPrec)
                bPrec.add(precision)
                bVal.add(scaledNextB)
            } else {
                // Reuse previous approximation to reduce sqrt iterations,
                // hopefully to one.
                val nextBAsCR = SqrtCR(bProdAsCR, bPrec[n + 1]!!, bVal[n + 1]!!)
                nextB = nextBAsCR.approxGet(evalPrec)
                // We assume that set() doesn't throw for any reason.
                bPrec[n + 1] = precision
                bVal[n + 1] = scale(nextB, -extraEvalPrec)
            }
            // bPrec.size() == bVal.size() >= n + 2
            val nextT = t.subtract(aDiff.multiply(aDiff).shiftLeft(n + evalPrec))  // shift dist. usually neg.
            a = nextA
            b = nextB
            t = nextT
            ++n
        }
        val sum = a.add(b)
        val result = sum.multiply(sum).divide(t).shiftRight(2)
        return scale(result, -extraEvalPrec)
    }

    companion object {

        private val TOLERANCE = BigInteger.valueOf(4)
        // sqrt(1/2)
        private val SQRT_HALF = SqrtCR(ONE.shiftRight(1))
    }
}

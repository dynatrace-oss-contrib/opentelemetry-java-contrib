/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public final class OtelTraceState {

  public static final String TRACE_STATE_KEY = "ot";

  private static final char P_SUBKEY = 'p';
  private static final char R_SUBKEY = 'r';
  private static final int MAX_P = 63;
  private static final int MAX_R = 62;
  private static final int INVALID_P = -1;
  private static final int INVALID_R = -1;
  private static final int TRACE_STATE_SIZE_LIMIT = 256;

  private int rval; // valid in the interval [0, MAX_R]
  private int pval; // valid in the interval [0, MAX_P]

  private final List<String> otherKeyValuePairs;

  private OtelTraceState(int rvalue, int pvalue, List<String> otherKeyValuePairs) {
    this.rval = rvalue;
    this.pval = pvalue;
    this.otherKeyValuePairs = otherKeyValuePairs;
  }

  private OtelTraceState() {
    this(INVALID_R, INVALID_P, Collections.emptyList());
  }

  public boolean hasValidR() {
    return isValidR(rval);
  }

  public boolean hasValidP() {
    return isValidP(pval);
  }

  public void invalidateP() {
    pval = INVALID_P;
  }

  public void invalidateR() {
    rval = INVALID_R;
  }

  /**
   * Sets a new p-value.
   *
   * <p>If the given p-value is invalid, the current p-value is invalidated.
   *
   * @param pval the new p-value
   */
  public void setP(int pval) {
    if (isValidP(pval)) {
      this.pval = pval;
    } else {
      invalidateP();
    }
  }

  /**
   * Sets a new r-value.
   *
   * <p>If the given r-value is invalid, the current r-value is invalidated.
   *
   * @param rval the new r-value
   */
  public void setR(int rval) {
    if (isValidR(rval)) {
      this.rval = rval;
    } else {
      invalidateR();
    }
  }

  /**
   * Returns a string representing this state.
   *
   * @return a string
   */
  public String serialize() {
    StringBuilder sb = new StringBuilder();
    if (hasValidP()) {
      sb.append("p:").append(pval);
    }
    if (hasValidR()) {
      if (sb.length() > 0) {
        sb.append(';');
      }
      sb.append("r:").append(rval);
    }
    for (String pair : otherKeyValuePairs) {
      int ex = sb.length();
      if (ex != 0) {
        ex += 1;
      }
      if (ex + pair.length() > TRACE_STATE_SIZE_LIMIT) {
        break;
      }
      if (sb.length() > 0) {
        sb.append(';');
      }
      sb.append(pair);
    }
    return sb.toString();
  }

  private static boolean isValueByte(char c) {
    if (isLowerCaseAlphaNum(c)) {
      return true;
    }
    if (isUpperCaseAlpha(c)) {
      return true;
    }
    return c == '.' || c == '_' || c == '-';
  }

  private static boolean isLowerCaseAlphaNum(char c) {
    return isLowerCaseAlpha(c) || isDigit(c);
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isLowerCaseAlpha(char c) {
    return c >= 'a' && c <= 'z';
  }

  private static boolean isUpperCaseAlpha(char c) {
    return c >= 'A' && c <= 'Z';
  }

  private static int parseOneOrTwoDigitNumber(
      String ts, int from, int to, int twoDigitMaxValue, int invalidValue) {
    if (to - from == 1) {
      char c = ts.charAt(from);
      if (isDigit(c)) {
        return c - '0';
      }
    } else if (to - from == 2) {
      char c1 = ts.charAt(from);
      char c2 = ts.charAt(from + 1);
      if (isDigit(c1) && isDigit(c2)) {
        int v = (c1 - '0') * 10 + (c2 - '0');
        if (v <= twoDigitMaxValue) {
          return v;
        }
      }
    }
    return invalidValue;
  }

  public static boolean isValidR(int v) {
    return 0 <= v && v <= MAX_R;
  }

  public static boolean isValidP(int v) {
    return 0 <= v && v <= MAX_P;
  }

  /**
   * Parses the OtelTraceState from a given string.
   *
   * <p>If the string cannot be successfully parsed, a new empty OtelTraceState is returned.
   *
   * @param ts the string
   * @return the parsed OtelTraceState or a new empty OtelTraceState in case of parsing errors
   */
  public static OtelTraceState parse(@Nullable String ts) {
    List<String> otherKeyValuePairs = null;
    int p = INVALID_P;
    int r = INVALID_R;

    if (ts == null || ts.isEmpty()) {
      return new OtelTraceState();
    }

    if (ts.length() > TRACE_STATE_SIZE_LIMIT) {
      return new OtelTraceState();
    }

    int tsStartPos = 0;
    int len = ts.length();

    while (true) {
      int eqPos = tsStartPos;
      for (; eqPos < len; eqPos++) {
        char c = ts.charAt(eqPos);
        if (!isLowerCaseAlpha(c) && (!isDigit(c) || eqPos == tsStartPos)) {
          break;
        }
      }
      if (eqPos == tsStartPos || eqPos == len || ts.charAt(eqPos) != ':') {
        return new OtelTraceState();
      }

      int sepPos = eqPos + 1;
      for (; sepPos < len; sepPos++) {
        if (isValueByte(ts.charAt(sepPos))) {
          continue;
        }
        break;
      }

      if (eqPos - tsStartPos == 1 && ts.charAt(tsStartPos) == P_SUBKEY) {
        p = parseOneOrTwoDigitNumber(ts, eqPos + 1, sepPos, MAX_P, INVALID_P);
      } else if (eqPos - tsStartPos == 1 && ts.charAt(tsStartPos) == R_SUBKEY) {
        r = parseOneOrTwoDigitNumber(ts, eqPos + 1, sepPos, MAX_R, INVALID_R);
      } else {
        if (otherKeyValuePairs == null) {
          otherKeyValuePairs = new ArrayList<>();
        }
        otherKeyValuePairs.add(ts.substring(tsStartPos, sepPos));
      }

      if (sepPos < len && ts.charAt(sepPos) != ';') {
        return new OtelTraceState();
      }

      if (sepPos == len) {
        break;
      }

      tsStartPos = sepPos + 1;

      // test for a trailing ;
      if (tsStartPos == len) {
        return new OtelTraceState();
      }
    }

    return new OtelTraceState(
        r, p, (otherKeyValuePairs != null) ? otherKeyValuePairs : Collections.emptyList());
  }

  public int getR() {
    return rval;
  }

  public int getP() {
    return pval;
  }

  public static int getMaxP() {
    return MAX_P;
  }

  public static int getMaxR() {
    return MAX_R;
  }

  /**
   * Returns an r-value that is guaranteed to be invalid.
   *
   * <p>{@code isValidR(getInvalidR())} will always return true.
   *
   * @return an invalid r-value
   */
  public static int getInvalidR() {
    return INVALID_R;
  }

  /**
   * Returns a p-value that is guaranteed to be invalid.
   *
   * <p>{@code isValidP(getInvalidP())} will always return true.
   *
   * @return an invalid p-value
   */
  public static int getInvalidP() {
    return INVALID_P;
  }
}

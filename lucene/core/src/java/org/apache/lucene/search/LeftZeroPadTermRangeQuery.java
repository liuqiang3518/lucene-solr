package org.apache.lucene.search;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.ToStringUtils;
import org.apache.lucene.util.automaton.CompiledAutomaton;

// nocommit javadocs
public class LeftZeroPadTermRangeQuery extends MultiTermQuery {
  private final BytesRef lowerTerm;
  private final BytesRef upperTerm;
  private final boolean includeLower;
  private final boolean includeUpper;

  /**
   * Constructs a query selecting all terms greater/equal than <code>lowerTerm</code>
   * but less/equal than <code>upperTerm</code>. 
   * 
   * <p>
   * If an endpoint is null, it is said 
   * to be "open". Either or both endpoints may be open.  Open endpoints may not 
   * be exclusive (you can't select all but the first or last term without 
   * explicitly specifying the term to exclude.)
   * 
   * @param field The field that holds both lower and upper terms.
   * @param lowerTerm
   *          The term text at the lower end of the range
   * @param upperTerm
   *          The term text at the upper end of the range
   * @param includeLower
   *          If true, the <code>lowerTerm</code> is
   *          included in the range.
   * @param includeUpper
   *          If true, the <code>upperTerm</code> is
   *          included in the range.
   */
  public LeftZeroPadTermRangeQuery(String field, BytesRef lowerTerm, BytesRef upperTerm, boolean includeLower, boolean includeUpper) {
    super(field);
    this.lowerTerm = lowerTerm;
    this.upperTerm = upperTerm;
    this.includeLower = includeLower;
    this.includeUpper = includeUpper;
  }

  /** Returns the lower value of this range query */
  public BytesRef getLowerTerm() { return lowerTerm; }

  /** Returns the upper value of this range query */
  public BytesRef getUpperTerm() { return upperTerm; }
  
  /** Returns <code>true</code> if the lower endpoint is inclusive */
  public boolean includesLower() { return includeLower; }
  
  /** Returns <code>true</code> if the upper endpoint is inclusive */
  public boolean includesUpper() { return includeUpper; }
  
  private BytesRef leftZeroPad(int newLength, BytesRef term) {
    if (term == null) {
      return null;
    }
    byte[] bytes = new byte[newLength];
    if (newLength < term.length) {
      // This is actually OK: it means the query range is larger than what's in the index
      return term;
    }

    int prefix = newLength - term.length;
    for(int i=0;i<prefix;i++) {
      bytes[i] = 0;
    }
    System.arraycopy(term.bytes, term.offset, bytes, prefix, term.length);
    return new BytesRef(bytes);
  }

  @Override
  protected TermsEnum getTermsEnum(Terms terms, AttributeSource atts) throws IOException {

    int fixedLength = terms.getMin().length;
    assert fixedLength == terms.getMax().length;

    boolean segIncludeUpper = includeUpper;

    if (lowerTerm.length > fixedLength) {
      return TermsEnum.EMPTY;
    }

    BytesRef segUpperTerm;
    if (upperTerm.length > fixedLength) {
      segUpperTerm = null;
    } else {
      segUpperTerm = leftZeroPad(fixedLength, upperTerm);
    }

    // Zero-pad by segment:
    BytesRef segLowerTerm = leftZeroPad(fixedLength, lowerTerm);
    
    if (segLowerTerm != null && segUpperTerm != null && segLowerTerm.compareTo(segUpperTerm) > 0) {
      // Matches no terms:
      return TermsEnum.EMPTY;
    }

    if (terms.size() == 0) {
      // No terms
      return TermsEnum.EMPTY;
    }

    // Optimization: if our range is outside of the range indexed in this segment, skip it:
    if (segUpperTerm != null && terms.getMin().compareTo(segUpperTerm) > 0) {
      return TermsEnum.EMPTY;
    }

    if (segLowerTerm != null && terms.getMax().compareTo(segLowerTerm) < 0) {
      return TermsEnum.EMPTY;
    }      
     
    TermsEnum tenum = terms.iterator(null);
    
    if ((segLowerTerm == null || (includeLower && segLowerTerm.length == 0)) && segUpperTerm == null) {
      // Matches all terms:
      return terms.iterator(null);
    }

    return new CompiledAutomaton(segLowerTerm, segLowerTerm == null || includeLower, segUpperTerm, segUpperTerm == null || includeUpper).getTermsEnum(terms);
  }

  /** Prints a user-readable version of this query. */
  @Override
  public String toString(String field) {
      StringBuilder buffer = new StringBuilder();
      if (!getField().equals(field)) {
          buffer.append(getField());
          buffer.append(":");
      }
      buffer.append(includeLower ? '[' : '{');
      // TODO: all these toStrings for queries should just output the bytes, it might not be UTF-8!
      buffer.append(lowerTerm != null ? ("*".equals(Term.toString(lowerTerm)) ? "\\*" : Term.toString(lowerTerm))  : "*");
      buffer.append(" TO ");
      buffer.append(upperTerm != null ? ("*".equals(Term.toString(upperTerm)) ? "\\*" : Term.toString(upperTerm)) : "*");
      buffer.append(includeUpper ? ']' : '}');
      buffer.append(ToStringUtils.boost(getBoost()));
      return buffer.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + (includeLower ? 1231 : 1237);
    result = prime * result + (includeUpper ? 1231 : 1237);
    result = prime * result + ((lowerTerm == null) ? 0 : lowerTerm.hashCode());
    result = prime * result + ((upperTerm == null) ? 0 : upperTerm.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    LeftZeroPadTermRangeQuery other = (LeftZeroPadTermRangeQuery) obj;
    if (includeLower != other.includeLower)
      return false;
    if (includeUpper != other.includeUpper)
      return false;
    if (lowerTerm == null) {
      if (other.lowerTerm != null)
        return false;
    } else if (!lowerTerm.equals(other.lowerTerm))
      return false;
    if (upperTerm == null) {
      if (other.upperTerm != null)
        return false;
    } else if (!upperTerm.equals(other.upperTerm))
      return false;
    return true;
  }

}
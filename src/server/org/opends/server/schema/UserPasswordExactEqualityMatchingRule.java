/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.schema;



import static org.opends.server.schema.SchemaConstants.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.StaticUtils;



/**
 * This class implements the userPasswordExactMatch matching rule, which will
 * simply compare encoded hashed password values to see if they are exactly
 * equal to each other.
 */
class UserPasswordExactEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * Creates a new instance of this userPasswordExactMatch matching rule.
   */
  public UserPasswordExactEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getAllNames()
  {
    return Collections.singleton(getName());
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  @Override
  public String getName()
  {
    return EMR_USER_PASSWORD_EXACT_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_USER_PASSWORD_EXACT_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  @Override
  public String getDescription()
  {
    // There is no standard description for this matching rule.
    return EMR_USER_PASSWORD_EXACT_DESCRIPTION;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  @Override
  public String getSyntaxOID()
  {
    return SYNTAX_USER_PASSWORD_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DirectoryException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeValue(ByteSequence value)
         throws DirectoryException
  {
    // The normalized form of this matching rule is exactly equal to the
    // non-normalized form, except that the scheme needs to be converted to
    // lowercase (if there is one).

    if (UserPasswordSyntax.isEncoded(value))
    {
      StringBuilder builder = new StringBuilder(value.length());
      int closingBracePos = -1;
      for (int i=1; i < value.length(); i++)
      {
        if (value.byteAt(i) == '}')
        {
          closingBracePos = i;
          break;
        }
      }
      ByteSequence seq1 = value.subSequence(0, closingBracePos + 1);
      ByteSequence seq2 =
        value.subSequence(closingBracePos + 1, value.length());
      StaticUtils.toLowerCase(seq1, builder, false);
      builder.append(seq2);
      return ByteString.valueOf(builder.toString());
    }
    else
    {
      return value.toByteString();
    }
  }
}


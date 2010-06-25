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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import java.util.List;

import org.opends.sdk.ByteString;
import org.opends.sdk.DecodeException;
import org.opends.sdk.DecodeOptions;
import org.opends.sdk.controls.Control;
import org.opends.sdk.controls.ControlDecoder;
import org.opends.sdk.responses.ExtendedResultDecoder;
import org.opends.sdk.responses.PasswordModifyExtendedResult;



/**
 * The password modify extended request as defined in RFC 3062. This operation
 * allows directory clients to update user passwords. The user may or may not be
 * associated with a directory entry. The user may or may not be represented as
 * an LDAP DN. The user's password may or may not be stored in the directory. In
 * addition, it includes support for requiring the user's current password as
 * well as for generating a new password if none was provided.
 *
 * @see PasswordModifyExtendedResult
 * @see <a href="http://tools.ietf.org/html/rfc3909">RFC 3062 - LDAP Password
 *      Modify Extended Operation </a>
 */
public interface PasswordModifyExtendedRequest extends
    ExtendedRequest<PasswordModifyExtendedResult>
{

  /**
   * The OID for the password modify extended operation request.
   */
  public static final String OID = "1.3.6.1.4.1.4203.1.11.1";

  /**
   * A decoder which can be used to decode password modify extended operation
   * requests.
   */
  public static final ExtendedRequestDecoder<PasswordModifyExtendedRequest,
                                             PasswordModifyExtendedResult>
    DECODER = new PasswordModifyExtendedRequestImpl.RequestDecoder();



  /**
   * {@inheritDoc}
   */
  PasswordModifyExtendedRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  <C extends Control> C getControl(ControlDecoder<C> decoder,
      DecodeOptions options) throws NullPointerException, DecodeException;



  /**
   * {@inheritDoc}
   */
  List<Control> getControls();



  /**
   * Returns the desired password for the user, or {@code null} if a new
   * password should be generated.
   *
   * @return The desired password for the user, or {@code null} if a new
   *         password should be generated.
   */
  ByteString getNewPassword();



  /**
   * Returns the desired password for the user decoded as a UTF-8 string, or
   * {@code null} if a new password should be generated.
   *
   * @return The desired password for the user decoded as a UTF-8 string, or
   *         {@code null} if a new password should be generated.
   */
  String getNewPasswordAsString();



  /**
   * {@inheritDoc}
   */
  String getOID();



  /**
   * Returns the current password for the user, if known.
   *
   * @return The current password for the user, or {@code null} if the password
   *         is not known.
   */
  ByteString getOldPassword();



  /**
   * Returns the current password for the user, if known, decoded as a UTF-8
   * string.
   *
   * @return The current password for the user decoded as a UTF-8 string, or
   *         {@code null} if the password is not known.
   */
  String getOldPasswordAsString();



  /**
   * {@inheritDoc}
   */
  ExtendedResultDecoder<PasswordModifyExtendedResult> getResultDecoder();



  /**
   * Returns the identity of the user whose password is to be modified, or
   * {@code null} if the request should be applied to the user currently
   * associated with the session. The returned identity may or may not be a
   * distinguished name.
   *
   * @return The identity of the user whose password is to be modified, or
   *         {@code null} if the request should be applied to the user currently
   *         associated with the session.
   */
  ByteString getUserIdentity();



  /**
   * Returns the identity of the user whose password is to be modified decoded
   * as a UTF-8 string, or {@code null} if the request should be applied to the
   * user currently associated with the session. The returned identity may or
   * may not be a distinguished name.
   *
   * @return The identity of the user whose password is to be modified decoded
   *         as a UTF-8 string, or {@code null} if the request should be applied
   *         to the user currently associated with the session.
   */
  String getUserIdentityAsString();



  /**
   * {@inheritDoc}
   */
  ByteString getValue();



  /**
   * {@inheritDoc}
   */
  boolean hasValue();



  /**
   * Sets the desired password for the user.
   *
   * @param newPassword
   *          The desired password for the user, or {@code null} if a new
   *          password should be generated.
   * @return This password modify request.
   */
  PasswordModifyExtendedRequest setNewPassword(ByteString newPassword);



  /**
   * Sets the desired password for the user. The password will be converted to a
   * UTF-8 octet string.
   *
   * @param newPassword
   *          The desired password for the user, or {@code null} if a new
   *          password should be generated.
   * @return This password modify request.
   */
  PasswordModifyExtendedRequest setNewPassword(String newPassword);



  /**
   * Sets the current password for the user.
   *
   * @param oldPassword
   *          The current password for the user, or {@code null} if the password
   *          is not known.
   * @return This password modify request.
   */
  PasswordModifyExtendedRequest setOldPassword(ByteString oldPassword);



  /**
   * Sets the current password for the user. The password will be converted to a
   * UTF-8 octet string.
   *
   * @param oldPassword
   *          The current password for the user, or {@code null} if the password
   *          is not known.
   * @return This password modify request.
   */
  PasswordModifyExtendedRequest setOldPassword(String oldPassword);



  /**
   * Sets the identity of the user whose password is to be modified. The
   * identity may or may not be a distinguished name.
   *
   * @param userIdentity
   *          The identity of the user whose password is to be modified, or
   *          {@code null} if the request should be applied to the user
   *          currently associated with the session.
   * @return This password modify request.
   */
  PasswordModifyExtendedRequest setUserIdentity(ByteString userIdentity);



  /**
   * Sets the identity of the user whose password is to be modified. The
   * identity may or may not be a distinguished name. The identity will be
   * converted to a UTF-8 octet string.
   *
   * @param userIdentity
   *          The identity of the user whose password is to be modified, or
   *          {@code null} if the request should be applied to the user
   *          currently associated with the session.
   * @return This password modify request.
   */
  PasswordModifyExtendedRequest setUserIdentity(String userIdentity);

}
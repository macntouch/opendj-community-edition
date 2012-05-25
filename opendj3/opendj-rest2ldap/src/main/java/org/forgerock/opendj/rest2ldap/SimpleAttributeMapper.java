/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright 2012 ForgeRock AS. All rights reserved.
 */

package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Utils.attributeToJson;
import static org.forgerock.opendj.rest2ldap.Utils.toLowerCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.resource.provider.Context;

/**
 *
 */
public class SimpleAttributeMapper implements AttributeMapper {

    private final String ldapAttributeName;
    private final String jsonAttributeName;
    private final String normalizedJsonAttributeName;

    /**
     * Creates a new simple attribute mapper which maps a single LDAP attribute
     * to an entry.
     *
     * @param attributeName
     *            The name of the simple JSON and LDAP attribute.
     */
    public SimpleAttributeMapper(String attributeName) {
        this(attributeName, attributeName);
    }

    /**
     * Creates a new simple attribute mapper which maps a single LDAP attribute
     * to an entry.
     *
     * @param jsonAttributeName
     *            The name of the simple JSON attribute.
     * @param ldapAttributeName
     *            The name of the LDAP attribute.
     */
    public SimpleAttributeMapper(String jsonAttributeName, String ldapAttributeName) {
        this.jsonAttributeName = jsonAttributeName;
        this.normalizedJsonAttributeName = toLowerCase(jsonAttributeName);
        this.ldapAttributeName = ldapAttributeName;
    }

    /**
     * {@inheritDoc}
     */
    public void getLDAPAttributes(Set<String> ldapAttributes) {
        ldapAttributes.add(ldapAttributeName);
    }

    /**
     * {@inheritDoc}
     */
    public void getLDAPAttributes(Set<String> ldapAttributes, JsonPointer resourceAttribute) {
        if (toLowerCase(resourceAttribute.leaf()).equals(normalizedJsonAttributeName)) {
            ldapAttributes.add(ldapAttributeName);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void toJson(Context c, Entry e,
            final AttributeMapperCompletionHandler<Map<String, Object>> h) {
        Attribute a = e.getAttribute(ldapAttributeName);
        if (a != null) {
            Map<String, Object> result =
                    Collections.singletonMap(jsonAttributeName, attributeToJson(a));
            h.onSuccess(result);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void toLDAP(Context c, JsonValue v, AttributeMapperCompletionHandler<List<Attribute>> h) {
        // TODO Auto-generated method stub

    }

}
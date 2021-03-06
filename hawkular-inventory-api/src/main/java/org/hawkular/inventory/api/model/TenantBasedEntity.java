/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.inventory.api.model;

import java.util.Map;

/**
 * Base class for entities in a tenant (i.e. everything but the {@link Tenant tenant}s themselves and relationships).
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public abstract class TenantBasedEntity<B extends Entity.Blueprint, U extends AbstractElement.Update>
        extends Entity<B, U> {

    /**
     * JAXB support
     */
    TenantBasedEntity() {
    }

    TenantBasedEntity(CanonicalPath path) {
        this(path, null);
    }

    TenantBasedEntity(CanonicalPath path, Map<String, Object> properties) {
        super(path, properties);
        if (!Tenant.class.equals(path.getRoot().getSegment().getElementType())) {
            throw new IllegalArgumentException("A tenant based entity should be rooted at a tenant.");
        }
    }

    public String getTenantId() {
        return getPath().getRoot().getSegment().getElementId();
    }
}

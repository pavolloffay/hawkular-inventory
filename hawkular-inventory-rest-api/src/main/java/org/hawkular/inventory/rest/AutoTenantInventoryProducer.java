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
package org.hawkular.inventory.rest;

import java.io.InputStream;
import java.util.Iterator;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.EmptyInventory;
import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Interest;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.Tenants;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.cdi.Official;

import rx.Observable;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
@ApplicationScoped
public class AutoTenantInventoryProducer {

    @Inject
    @Official
    private Inventory inventory;

    @Produces
    @AutoTenant
    @ApplicationScoped
    public Inventory getInventory() {
        return new Inventory() {
            @Override
            public void initialize(Configuration configuration) {
                inventory.initialize(configuration);
            }

            @Override
            public Tenants.ReadWrite tenants() {
                Tenants.ReadWrite actual = inventory.tenants();
                return new Tenants.ReadWrite() {
                    @Override
                    public Tenants.Multiple getAll(Filter[][] filters) {
                        return new EmptyInventory.TenantsMultiple();
                    }

                    @Override
                    public Tenants.Single get(String id) throws EntityNotFoundException {
                        Tenants.Single ret = actual.get(id);
                        if (ret.exists()) {
                            return ret;
                        } else {
                            return actual.create(Tenant.Blueprint.builder().withId(id).build());
                        }
                    }

                    @Override
                    public Tenants.Single create(Tenant.Blueprint blueprint) throws EntityAlreadyExistsException {
                        return actual.create(blueprint);
                    }

                    @Override
                    public void update(String id, Tenant.Update update) throws EntityNotFoundException {
                        actual.update(id, update);
                    }

                    @Override
                    public void delete(String id) throws EntityNotFoundException {
                        actual.delete(id);
                    }
                };
            }

            @Override
            public Relationships.Read relationships() {
                return inventory.relationships();
            }

            @Override
            public boolean hasObservers(Interest<?, ?> interest) {
                return inventory.hasObservers(interest);
            }

            @Override
            public <C, E> Observable<C> observable(Interest<C, E> interest) {
                return inventory.observable(interest);
            }

            @Override
            public InputStream getGraphSON(String tenantId) {
                return inventory.getGraphSON(tenantId);
            }

            @Override
            public <T extends Entity<?, ?>> Iterator<T> getTransitiveClosureOver(CanonicalPath startingPoint,
                                                                                 Relationships.Direction direction,
                                                                         Class<T> clazz, String... relationshipNames) {
                return inventory.getTransitiveClosureOver(startingPoint, direction, clazz, relationshipNames);
            }

            @Override
            public void close() throws Exception {
                inventory.close();
            }
        };
    }
}

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
package org.hawkular.inventory.impl.tinkerpop.test;

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.GraphQuery;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.wrappers.wrapped.WrappedGraph;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.feeds.AcceptWithFallbackFeedIdStrategy;
import org.hawkular.inventory.api.feeds.RandomUUIDFeedIdStrategy;
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.model.Version;
import org.hawkular.inventory.impl.tinkerpop.InventoryService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.StreamSupport;

/**
 * Test some basic functionality
 *
 * @author Lukas Krejci
 * @author Jirka Kremser
 */
public class BasicTest {

    TransactionalGraph graph;
    InventoryService inventory;

    @Before
    public void setup() throws Exception {
        Configuration config = Configuration.builder().withFeedIdStrategy(
                new AcceptWithFallbackFeedIdStrategy(new RandomUUIDFeedIdStrategy()))
                .addConfigurationProperty("blueprints.graph",
                        "org.hawkular.inventory.impl.tinkerpop.test.BasicTest$DummyTransactionalGraph")
                .addConfigurationProperty("blueprints.tg.directory", new File("./__tinker.graph").getAbsolutePath())
                .build();

        inventory = new InventoryService();
        inventory.initialize(config);

        graph = inventory.getGraph();

        setupData();
    }

    private void setupData() throws Exception {
        assert inventory.tenants().create("com.acme.tenant").entity().getId().equals("com.acme.tenant");
        assert inventory.tenants().get("com.acme.tenant").environments().create("production").entity().getId()
                .equals("production");
        assert inventory.tenants().get("com.acme.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("URL", new Version("1.0"))).entity().getId().equals("URL");
        assert inventory.tenants().get("com.acme.tenant").metricTypes()
                .create(new MetricType.Blueprint("ResponseTime", MetricUnit.MILLI_SECOND)).entity().getId()
                .equals("ResponseTime");

        inventory.tenants().get("com.acme.tenant").resourceTypes().get("URL").metricTypes().add("ResponseTime");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").metrics()
                .create(new Metric.Blueprint(
                        new MetricType("com.acme.tenant", "ResponseTime", MetricUnit.MILLI_SECOND),
                        "host1_ping_response")).entity().getId().equals("host1_ping_response");
        assert inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .create(new Resource.Blueprint("host1", new ResourceType("com.acme.tenant", "URL", "1.0"))).entity()
                .getId().equals("host1");
        inventory.tenants().get("com.acme.tenant").environments().get("production").resources()
                .get("host1").metrics().add("host1_ping_response");

        assert inventory.tenants().create("com.example.tenant").entity().getId().equals("com.example.tenant");
        assert inventory.tenants().get("com.example.tenant").environments().create("test").entity().getId()
                .equals("test");
        assert inventory.tenants().get("com.example.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("Kachna", new Version("1.0"))).entity().getId().equals("Kachna");
        assert inventory.tenants().get("com.example.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("Playroom", new Version("1.0"))).entity().getId().equals("Playroom");
        assert inventory.tenants().get("com.example.tenant").metricTypes()
                .create(new MetricType.Blueprint("Size", MetricUnit.BYTE)).entity().getId().equals("Size");
        inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").metricTypes().add("Size");

        assert inventory.tenants().get("com.example.tenant").environments().get("test").metrics()
                .create(new Metric.Blueprint(
                        new MetricType("com.example.tenant", "Size", MetricUnit.BYTE),
                        "playroom1_size")).entity().getId().equals("playroom1_size");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").metrics()
                .create(new Metric.Blueprint(
                        new MetricType("com.example.tenant", "Size", MetricUnit.BYTE),
                        "playroom2_size")).entity().getId().equals("playroom2_size");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .create(new Resource.Blueprint("playroom1", new ResourceType("com.example.tenant", "Playroom", "1.0")))
                .entity().getId().equals("playroom1");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .create(new Resource.Blueprint("playroom2", new ResourceType("com.example.tenant", "Playroom", "1.0")))
                .entity().getId().equals("playroom2");

        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom1").metrics().add("playroom1_size");
        inventory.tenants().get("com.example.tenant").environments().get("test").resources()
                .get("playroom2").metrics().add("playroom2_size");
    }

    @After
    public void teardown() throws Exception {
        inventory.close();
        deleteGraph();
    }

    private static void deleteGraph() throws Exception {
        Files.walkFileTree(Paths.get("./", "__tinker.graph"), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Test
    public void testTenants() throws Exception {
        Function<String, Void> test = (id) -> {
            GraphQuery q = graph.query().has("type", "tenant").has("uid", id);

            Iterator<Vertex> evs = q.vertices().iterator();
            assert evs.hasNext();
            Vertex ev = evs.next();
            assert !evs.hasNext();

            Tenant t = inventory.tenants().get(id).entity();

            assert ev.getProperty("uid").equals(id);
            assert t.getId().equals(id);
            return null;
        };

        test.apply("com.acme.tenant");
        test.apply("com.example.tenant");

        GraphQuery query = graph.query().has("type", "tenant");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 2;
    }

    @Test
    public void testEntitiesByRelationships() throws Exception {
        Function<Integer, Function<String, Function<String, Function<Integer, Function<String,
                Function<ResolvableToMany, Consumer<ResolvableToMany>>>>>>>
                testHelper = (numberOfParents -> parentType -> edgeLabel -> numberOfKids -> childType ->
                multipleParents -> multipleChildren -> {
                    GremlinPipeline<Graph, Vertex> q1 = new GremlinPipeline<Graph, Vertex>(graph)
                            .V().has("type", parentType).cast(Vertex.class);
                    Iterator<Vertex> parentIterator = q1.iterator();

                    GremlinPipeline<Graph, Vertex> q2 = new GremlinPipeline<Graph, Vertex>(graph)
                            .V().has("type", parentType).out(edgeLabel).has("type", childType)
                            .cast(Vertex.class);
                    Iterator<Vertex> childIterator = q2.iterator();

                    Iterator<Object> multipleParentIterator = multipleParents.entities().iterator();
                    Iterator<Object> multipleChildrenIterator = multipleChildren.entities().iterator();

                    for (int i = 0; i < numberOfParents; i++) {
                        assert parentIterator.hasNext() : "There must be exactly " + numberOfParents + " " +
                                parentType + "s " + "that have outgoing edge labeled with " + edgeLabel + ". Gremlin " +
                                "query returned only " + i;
                        assert multipleParentIterator.hasNext() : "There must be exactly " + numberOfParents + " " +
                                parentType + "s that have outgoing edge labeled with " + edgeLabel + ". Tested API " +
                                "returned only " + i;
                        parentIterator.next();
                        multipleParentIterator.next();
                    }
                    assert !parentIterator.hasNext() : "There must be " + numberOfParents + " " + parentType +
                            "s. Gremlin query returned more than " + numberOfParents;
                    assert !multipleParentIterator.hasNext() : "There must be " + numberOfParents + " " + parentType +
                            "s. Tested API returned more than " + numberOfParents;

                    for (int i = 0; i < numberOfKids; i++) {
                        assert childIterator.hasNext() : "There must be exactly " + numberOfKids + " " + childType +
                                "s that are directly under " + parentType + " connected with " + edgeLabel +
                                ". Gremlin query returned only " + i;
                        assert multipleChildrenIterator.hasNext();
                        childIterator.next();
                        multipleChildrenIterator.next();
                    }
                    assert !childIterator.hasNext() : "There must be exactly " + numberOfKids + " " + childType + "s";
                    assert !multipleChildrenIterator.hasNext();
                });

        ResolvableToMany parents = inventory.tenants().getAll(Related.by("contains"));
        ResolvableToMany kids = inventory.tenants().getAll().environments().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply("tenant").apply("contains").apply(2).apply("environment").apply(parents).accept(kids);

        kids = inventory.tenants().getAll().resourceTypes().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply("tenant").apply("contains").apply(3).apply("resourceType").apply(parents)
                .accept(kids);

        kids = inventory.tenants().getAll().metricTypes().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply("tenant").apply("contains").apply(2).apply("metricType").apply(parents)
                .accept(kids);

        parents = inventory.tenants().getAll().environments().getAll(Related.by("contains"));
        kids = inventory.tenants().getAll().environments().getAll().metrics().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply("environment").apply("contains").apply(3).apply("metric").apply(parents).
                accept(kids);

        kids = inventory.tenants().getAll().environments().getAll().resources().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply("environment").apply("contains").apply(3).apply("resource").apply(parents).accept
                (kids);

        parents = inventory.tenants().getAll().environments().getAll(Related.by("contains"));
        kids = inventory.tenants().getAll().environments().getAll().metrics().getAll(Related.asTargetBy("defines"));
        testHelper.apply(2).apply("metricType").apply("defines").apply(3).apply("metric").apply(parents)
                .accept(kids);
    }

    @Test
    public void testRelationshipServiceGet() throws Exception {
        // the edge IDs are set deterministically for dummy graph, but this may differ with graph implementation
        // here we assume the edge IDs in advance to be 10 and 14 and there is not edge with id 13

        Relationship contains = inventory.tenants().get("com.example.tenant").relationships().get("10").entity();

        assert "com.example.tenant".equals(contains.getSource().getId()) : "Source node of the very first relation " +
                "going from tenants must have uid equal to 'com.acme.tenant'";
        assert "Kachna".equals(contains.getTarget().getId()) : "Target node of the very first relation going  " +
                "from tenants must have uid equal to 'production'";

        contains = inventory.tenants().getAll().environments().get("test").relationships().get("14").entity();

        assert "test".equals(contains.getSource().getId()) : "Source node of the very first relation " +
                "going from environments must have uid equal to 'com.example.tenant'. Was: " + contains.getSource()
                .getId();
        assert "playroom1_size" .equals(contains.getTarget().getId()) : "Target node of the very first relation  " +
            "going from  environments must have uid equal to 'test'. Was: " + contains.getTarget().getId();
        assert "contains" .equals(contains.getName()) : "Name of the relation must be 'contains'.";

        contains = inventory.tenants().getAll().environments().get("test").relationships().get("13").entity();
        assert contains == null : "There should not be any edge with id 13.";
    }

    @Test
    public void testRelationshipServiceNamed1() throws Exception {
        Set<Relationship> contains = inventory.tenants().getAll().relationships().named("contains").entities();
        assert contains.stream().anyMatch(rel -> "com.acme.tenant".equals(rel.getSource().getId())
                && "URL".equals(rel.getTarget().getId()))
                : "Tenant 'com.acme.tenant' must contain ResourceType 'URL'.";
        assert contains.stream().anyMatch(rel -> "com.acme.tenant".equals(rel.getSource().getId())
                && "production" .equals(rel.getTarget().getId()))
                : "Tenant 'com.acme.tenant' must contain Environment 'production'.";
        assert contains.stream().anyMatch(rel -> "com.example.tenant".equals(rel.getSource().getId())
                && "Size".equals(rel.getTarget().getId()))
                : "Tenant 'com.example.tenant' must contain MetricType 'Size'.";
    }

    @Test
    public void testRelationshipServiceNamed2() throws Exception {
        Set<Relationship> contains = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships().named("contains").entities();
        assert contains.stream().anyMatch(rel -> "playroom1" .equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom1'.";
        assert contains.stream().anyMatch(rel -> "playroom2" .equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom2'.";
        assert contains.stream().anyMatch(rel -> "playroom2_size" .equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom2_size'.";
        assert contains.stream().anyMatch(rel -> "playroom1_size" .equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom1_size'.";
        assert contains.stream().allMatch(rel -> !"production" .equals(rel.getSource().getId()))
                : "Environment 'production' cant be the source of these relationships.";
    }

    @Test
    public void testRelationshipServiceCallChaining() throws Exception {
        Set<Relationship> contains = inventory.tenants().getAll().environments().get("test").relationships().named
                ("contains").entities();

        MetricType metricType = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom")
                .relationships().named("owns").metricTypes().get("Size").entity();// not empty
        assert "Size".equals(metricType.getId()) : "ResourceType[Playroom] -owns-> MetricType[Size] was not found";

        try {
            metricType = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").relationships()
                    .named("contains").metricTypes().get("Size").entity();
            assert false : "There is no such an entity satisfying the query, this code shouldn't be reachable";
        } catch (EntityNotFoundException e) {
            // good
        }

        Set<Resource> resources = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom")
                .relationships().named
                ("defines").resources().getAll().entities();
        assert resources.stream().allMatch(res -> "playroom1".equals(res.getId()) || "playroom2".equals(res.getId()))
                : "ResourceType[Playroom] -defines-> resources called playroom1 and playroom2";

        resources = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").relationships().named
                ("owns").resources().getAll().entities(); // empty
        assert resources.isEmpty()
                : "No resources should be found under the relationship called owns from resource type";
    }

    @Test
    public void testEnvironments() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph)
                    .V().has("type", "tenant").has("uid", tenantId).out("contains")
                    .has("type", "environment").has("uid", id).cast(Vertex.class);

            Iterator<Vertex> envs = q.iterator();
            assert envs.hasNext();
            envs.next();
            assert !envs.hasNext();

            //query, we should get the same results
            Environment env = inventory.tenants().get(tenantId).environments().get(id).entity();
            assert env.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production");
        test.apply("com.example.tenant", "test");

        GraphQuery query = graph.query().has("type", "environment");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 2;
    }

    @Test
    public void testResourceTypes() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "resourceType").has("uid", id)
                    .has("version", "1.0").cast(Vertex.class);

            assert q.hasNext();

            ResourceType rt = inventory.tenants().get(tenantId).resourceTypes().get(id).entity();
            assert rt.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL");
        test.apply("com.example.tenant", "Kachna");
        test.apply("com.example.tenant", "Playroom");

        GraphQuery query = graph.query().has("type", "resourceType");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 3;
    }

    @Test
    public void testMetricDefinitions() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {

            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "metricType")
                    .has("uid", id).cast(Vertex.class);

            assert q.hasNext();

            MetricType md = inventory.tenants().get(tenantId).metricTypes().get(id).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "ResponseTime");
        test.apply("com.example.tenant", "Size");

        GraphQuery query = graph.query().has("type", "metricType");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 2;
    }

    @Test
    public void testMetricDefsLinkedToResourceTypes() throws Exception {
        TriFunction<String, String, String, Void> test = (tenantId, resourceTypeId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "resourceType")
                    .has("uid", resourceTypeId).out("owns").has("type", "metricType").has("uid", id)
                    .cast(Vertex.class);

            assert q.hasNext();

            MetricType md = inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId)
                    .metricTypes().get(id).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL", "ResponseTime");
        test.apply("com.example.tenant", "Playroom", "Size");
    }

    @Test
    public void testMetrics() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, metricDefId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "environment").has("uid", environmentId)
                    .out("contains").has("type", "metric").has("uid", id).as("metric").in("defines")
                    .has("type", "metricType").has("uid", metricDefId).back("metric").cast(Vertex.class);

            assert q.hasNext();

            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).metrics()
                    .getAll(Defined.by(new MetricType(tenantId, metricDefId)), With.id(id)).entities().iterator()
                    .next();
            assert m.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production", "ResponseTime", "host1_ping_response");
        test.apply("com.example.tenant", "test", "Size", "playroom1_size");
        test.apply("com.example.tenant", "test", "Size", "playroom2_size");

        GraphQuery query = graph.query().has("type", "metric");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 3;
    }

    @Test
    public void testResources() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceTypeId, id) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "environment").has("uid", environmentId)
                    .out("contains").has("type", "resource").has("uid", id).as("resource").in("defines")
                    .has("type", "resourceType").has("uid", resourceTypeId).back("resource").cast(Vertex.class);

            assert q.hasNext();

            Resource r = inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                    .getAll(Defined.by(new ResourceType(tenantId, resourceTypeId, "1.0")), With.id(id)).entities()
                    .iterator().next();
            assert r.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production", "URL", "host1");
        test.apply("com.example.tenant", "test", "Playroom", "playroom1");
        test.apply("com.example.tenant", "test", "Playroom", "playroom2");

        GraphQuery query = graph.query().has("type", "resource");
        assert StreamSupport.stream(query.vertices().spliterator(), false).count() == 3;
    }

    @Test
    public void testAssociateMetricWithResource() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceId, metricId) -> {
            GremlinPipeline<Graph, Vertex> q = new GremlinPipeline<Graph, Vertex>(graph).V().has("type", "tenant")
                    .has("uid", tenantId).out("contains").has("type", "environment").has("uid", environmentId)
                    .out("contains").has("type", "resource").has("uid", resourceId).out("owns")
                    .has("type", "metric").has("uid", metricId).cast(Vertex.class);

            assert q.hasNext();

            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).resources()
                    .get(resourceId).metrics().get(metricId).entity();
            assert metricId.equals(m.getId());

            return null;
        };

        test.apply("com.acme.tenant", "production", "host1", "host1_ping_response");
        test.apply("com.example.tenant", "test", "playroom1", "playroom1_size");
        test.apply("com.example.tenant", "test", "playroom2", "playroom2_size");
    }

    @Test
    public void queryMultipleTenants() throws Exception {
        Set<Tenant> tenants = inventory.tenants().getAll().entities();

        assert tenants.size() == 2;
    }

    @Test
    public void queryMultipleEnvironments() throws Exception {
        Set<Environment> environments = inventory.tenants().getAll().environments().getAll().entities();

        assert environments.size() == 2;
    }

    @Test
    public void queryMultipleResourceTypes() throws Exception {
        Set<ResourceType> types = inventory.tenants().getAll().resourceTypes().getAll().entities();
        assert types.size() == 3;
    }

    @Test
    public void queryMultipleMetricDefs() throws Exception {
        Set<MetricType> types = inventory.tenants().getAll().metricTypes().getAll().entities();
        assert types.size() == 2;
    }

    @Test
    public void queryMultipleResources() throws Exception {
        Set<Resource> rs = inventory.tenants().getAll().environments().getAll().resources().getAll().entities();
        assert rs.size() == 3;
    }

    @Test
    public void queryMultipleMetrics() throws Exception {
        Set<Metric> ms = inventory.tenants().getAll().environments().getAll().metrics().getAll().entities();
        assert ms.size() == 3;
    }

    @Test
    public void testNoTwoFeedsWithSameID() throws Exception {
        Feeds.ReadAndRegister feeds = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .feeds();

        Feed f1 = feeds.register("feed").entity();
        Feed f2  = feeds.register("feed").entity();

        assert f1.getId().equals("feed");
        assert !f1.getId().equals(f2.getId());
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class DummyTransactionalGraph extends WrappedGraph<TinkerGraph> implements TransactionalGraph {

        public DummyTransactionalGraph(org.apache.commons.configuration.Configuration configuration) {
            super(new TinkerGraph(configuration));
        }

        @Override
        public void stopTransaction(Conclusion conclusion) {
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }
    }

    private interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }

    private interface TetraFunction<T, U, V, W, R> {
        R apply(T t, U u, V v, W w);
    }
}
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

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.rest.json.ApiError;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Path("/")
@Produces(value = APPLICATION_JSON)
@Consumes(value = APPLICATION_JSON)
@Api(value = "/", description = "Metrics CRUD")
public class RestMetrics {

    @Inject @ForRest
    private Inventory inventory;

    @POST
    @Path("/{tenantId}/{environmentId}/metrics")
    @ApiOperation("Creates a new metric in given environment")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Metric created"),
            @ApiResponse(code = 400, message = "Invalid inputs", response = ApiError.class),
            @ApiResponse(code = 409, message = "Metric already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response createMetric(@PathParam("tenantId") String tenantId,
                                 @PathParam("environmentId") String environmentId,
                                 @ApiParam(required = true) Metric.Blueprint metric,
                                 @Context UriInfo uriInfo) {

        createMetric(inventory.tenants().get(tenantId).environments().get(environmentId).feedlessMetrics(), metric);
        return ResponseUtil.created(uriInfo, metric.getId()).build();
    }

    @POST
    @Path("/{tenantId}/{environmentId}/{feedId}/metrics")
    @ApiOperation("Creates a new metric in given feed")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Metric created"),
            @ApiResponse(code = 400, message = "Invalid inputs", response = ApiError.class),
            @ApiResponse(code = 409, message = "Metric already exists", response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response createMetric(@PathParam("tenantId") String tenantId,
            @PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @ApiParam(required = true) Metric.Blueprint metric, @Context UriInfo uriInfo) {

        createMetric(inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).metrics(),
                metric);
        return ResponseUtil.created(uriInfo, metric.getId()).build();
    }

    private void createMetric(Metrics.ReadWrite accessInterface, Metric.Blueprint metric) {
        if (metric == null) {
            throw new IllegalArgumentException("metric to create not specified");
        }

        if (metric.getId() == null) {
            throw new IllegalArgumentException("metric id not specified");
        }

        if (metric.getMetricTypeId() == null) {
            throw new IllegalArgumentException("metric type id not specified");
        }

        accessInterface.create(metric);
    }

    @GET
    @Path("/{tenantId}/{environmentId}/metrics/{metricId}")
    @ApiOperation("Retrieves a single metric")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or metrics doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Metric getMetric(@PathParam("tenantId") String tenantId,
                            @PathParam("environmentId") String environmentId,
                            @PathParam("metricId") String metricId) {

        return inventory.tenants().get(tenantId).environments().get(environmentId).feedlessMetrics().get(metricId)
                .entity();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/{feedId}/metrics/{metricId}")
    @ApiOperation("Retrieves a single metric")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Metric getMetric(@PathParam("tenantId") String tenantId,
                            @PathParam("environmentId") String environmentId,
                            @PathParam("feedId") String feedId,
                            @PathParam("metricId") String metricId) {

        return inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).metrics()
                .get(metricId).entity();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/metrics")
    @ApiOperation("Retrieves all metrics in an environment. Accepts paging query parameters.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant or environment doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getMetrics(@PathParam("tenantId") String tenantId,
                               @PathParam("environmentId") String environmentId,
                               @QueryParam("feedless") @DefaultValue("false") boolean feedless,
                               @Context UriInfo uriInfo) {

        Environments.Single envs = inventory.tenants().get(tenantId).environments().get(environmentId);

        Page<Metric> ret = (feedless ? envs.feedlessMetrics() : envs.allMetrics())
                .getAll().entities(RequestUtil.extractPaging(uriInfo));
        return ResponseUtil.pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @GET
    @Path("/{tenantId}/{environmentId}/{feedId}/metrics")
    @ApiOperation("Retrieves all metrics in a feed")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or feed doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response getMetrics(@PathParam("tenantId") String tenantId,
                                  @PathParam("environmentId") String environmentId,
                                  @PathParam("feedId") String feedId,
                                  @Context UriInfo uriInfo) {

        Page<Metric> ret = inventory.tenants().get(tenantId).environments().get(environmentId).feeds()
                .get(feedId).metrics().getAll().entities(RequestUtil.extractPaging(uriInfo));
        return ResponseUtil.pagedResponse(Response.ok(), uriInfo, ret).build();
    }

    @PUT
    @Path("/{tenantId}/{environmentId}/metrics/{metricId}")
    @ApiOperation("Updates a metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or the metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 400, message = "The update failed because of invalid data"),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response updateMetric(@PathParam("tenantId") String tenantId,
                                 @PathParam("environmentId") String environmentId,
                                 @PathParam("metricId") String metricId,
                                 Metric.Update update) {
        inventory.tenants().get(tenantId).environments().get(environmentId).feedlessMetrics().update(metricId, update);
        return Response.noContent().build();
    }


    @PUT
    @Path("/{tenantId}/{environmentId}/{feedId}/metrics/{metricId}")
    @ApiOperation("Updates a metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or the metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 400, message = "The update failed because of invalid data"),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response updateMetric(@PathParam("tenantId") String tenantId,
            @PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("metricId") String metricId, Metric.Update update) {
        inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).metrics()
                .update(metricId, update);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{tenantId}/{environmentId}/metrics/{metricId}")
    @ApiOperation("Deletes a metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment or the metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 400, message = "The delete failed because it would make inventory invalid"),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteMetric(@PathParam("tenantId") String tenantId,
                                 @PathParam("environmentId") String environmentId,
                                 @PathParam("metricId") String metricId) {

        inventory.tenants().get(tenantId).environments().get(environmentId).feedlessMetrics().delete(metricId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{tenantId}/{environmentId}/{feedId}/metrics/{metricId}")
    @ApiOperation("Deletes a metric")
    @ApiResponses({
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 404, message = "Tenant, environment, feed or the metric doesn't exist",
                    response = ApiError.class),
            @ApiResponse(code = 400, message = "The delete failed because it would make inventory invalid"),
            @ApiResponse(code = 500, message = "Server error", response = ApiError.class)
    })
    public Response deleteMetric(@PathParam("tenantId") String tenantId,
            @PathParam("environmentId") String environmentId, @PathParam("feedId") String feedId,
            @PathParam("metricId") String metricId) {

        inventory.tenants().get(tenantId).environments().get(environmentId).feeds().get(feedId).metrics()
                .delete(metricId);
        return Response.noContent().build();
    }
}
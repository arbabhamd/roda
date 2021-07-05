package org.roda.wui.api.v1;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.JSONP;
import org.roda.core.common.UserUtility;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.v2.distributedInstance.DistributedInstance;
import org.roda.core.data.v2.distributedInstance.DistributedInstances;
import org.roda.core.data.v2.distributedInstance.LocalInstance;
import org.roda.core.data.v2.user.User;
import org.roda.wui.api.controllers.Browser;
import org.roda.wui.api.v1.utils.ApiResponseMessage;
import org.roda.wui.api.v1.utils.ApiUtils;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Gabriel Barros <gbarros@keep.pt>
 */

@Path(DistributedInstancesResource.ENDPOINT)
@Api(value = DistributedInstancesResource.SWAGGER_ENDPOINT)
public class DistributedInstancesResource {
  public static final String ENDPOINT = "/v1/distributed_instances";
  public static final String SWAGGER_ENDPOINT = "v1 distributed instances";

  @Context
  private HttpServletRequest request;

  @GET
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  @JSONP(callback = RodaConstants.API_QUERY_DEFAULT_JSONP_CALLBACK, queryParam = RodaConstants.API_QUERY_KEY_JSONP_CALLBACK)
  @ApiOperation(value = "List distributed instances", notes = "Gets a list of disposal schedule.", response = DistributedInstance.class, responseContainer = "List")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Successful response", response = DistributedInstance.class, responseContainer = "List"),
    @ApiResponse(code = 404, message = "Not found", response = ApiResponseMessage.class)})

  public Response listDistributedInstances(
    @ApiParam(value = "Choose format in which to get the distributed instances", allowableValues = RodaConstants.API_LIST_MEDIA_TYPES, defaultValue = RodaConstants.API_QUERY_VALUE_ACCEPT_FORMAT_JSON) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat,
    @ApiParam(value = "JSONP callback name") @QueryParam(RodaConstants.API_QUERY_KEY_JSONP_CALLBACK) String jsonpCallbackName)
    throws RODAException, IOException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    DistributedInstances distributedInstances = Browser.listDistributedInstances(user);

    return Response.ok(distributedInstances, mediaType).build();
  }

  @POST
  @Path("/register")
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  @JSONP(callback = RodaConstants.API_QUERY_DEFAULT_JSONP_CALLBACK, queryParam = RodaConstants.API_QUERY_KEY_JSONP_CALLBACK)
  @ApiOperation(value = "register a distributed instance", notes = "Register a new distributed instance", response = LocalInstance.class)
  @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", response = LocalInstance.class),
    @ApiResponse(code = 409, message = "Already exists", response = ApiResponseMessage.class)})

  public Response registerDistributedInstance(LocalInstance localInstance,
    @ApiParam(value = "Choose format in which to get the local instance configuration", allowableValues = RodaConstants.API_POST_PUT_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat,
    @ApiParam(value = "JSONP callback name") @QueryParam(RodaConstants.API_QUERY_KEY_JSONP_CALLBACK) String jsonpCallbackName)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    Browser.registerDistributedInstance(user, localInstance);

    return Response.ok(new ApiResponseMessage(ApiResponseMessage.OK, "Distributed instance registered"), mediaType)
      .build();
  }

  @POST
  @Path("/sync")
  @Consumes("multipart/*")
  public Response synchronize(FormDataMultiPart file,
                              @ApiParam(value = "Choose format in which to get the response", allowableValues = RodaConstants.API_POST_PUT_MEDIA_TYPES) @QueryParam(RodaConstants.API_QUERY_KEY_ACCEPT_FORMAT) String acceptFormat)
    throws RODAException {
    String mediaType = ApiUtils.getMediaType(acceptFormat, request);

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    Browser.importSyncBundle(user, file);

    return Response.ok(new ApiResponseMessage(ApiResponseMessage.OK, "Bundle entries imported"), mediaType).build();
  }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.guacamole.rest.connectiongroup;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.ConnectionGroup;
import org.apache.guacamole.net.auth.Directory;
import org.apache.guacamole.net.auth.UserContext;
import org.apache.guacamole.net.auth.permission.ObjectPermission;
import org.apache.guacamole.GuacamoleSession;
import org.apache.guacamole.rest.ObjectRetrievalService;
import org.apache.guacamole.rest.auth.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A REST Service for handling connection group CRUD operations.
 * 
 * @author James Muehlner
 */
@Path("/data/{dataSource}/connectionGroups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConnectionGroupRESTService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(ConnectionGroupRESTService.class);
    
    /**
     * A service for authenticating users from auth tokens.
     */
    @Inject
    private AuthenticationService authenticationService;
    
    /**
     * Service for convenient retrieval of objects.
     */
    @Inject
    private ObjectRetrievalService retrievalService;
    
    /**
     * Gets an individual connection group.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     * 
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext containing the connection group to be retrieved.
     *
     * @param connectionGroupID
     *     The ID of the connection group to retrieve.
     * 
     * @return
     *     The connection group, without any descendants.
     *
     * @throws GuacamoleException
     *     If a problem is encountered while retrieving the connection group.
     */
    @GET
    @Path("/{connectionGroupID}")
    public APIConnectionGroup getConnectionGroup(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("connectionGroupID") String connectionGroupID)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);

        // Retrieve the requested connection group
        return new APIConnectionGroup(retrievalService.retrieveConnectionGroup(session, authProviderIdentifier, connectionGroupID));

    }

    /**
     * Gets an individual connection group and all children.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext containing the connection group to be retrieved.
     *
     * @param connectionGroupID
     *     The ID of the connection group to retrieve.
     *
     * @param permissions
     *     If specified and non-empty, limit the returned list to only those
     *     connections for which the current user has any of the given
     *     permissions. Otherwise, all visible connections are returned.
     *     Connection groups are unaffected by this parameter.
     * 
     * @return
     *     The requested connection group, including all descendants.
     *
     * @throws GuacamoleException
     *     If a problem is encountered while retrieving the connection group or
     *     its descendants.
     */
    @GET
    @Path("/{connectionGroupID}/tree")
    public APIConnectionGroup getConnectionGroupTree(@QueryParam("token") String authToken, 
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("connectionGroupID") String connectionGroupID,
            @QueryParam("permission") List<ObjectPermission.Type> permissions)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);

        // Retrieve the requested tree, filtering by the given permissions
        ConnectionGroup treeRoot = retrievalService.retrieveConnectionGroup(userContext, connectionGroupID);
        ConnectionGroupTree tree = new ConnectionGroupTree(userContext, treeRoot, permissions);

        // Return tree as a connection group
        return tree.getRootAPIConnectionGroup();

    }

    /**
     * Deletes an individual connection group.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext containing the connection group to be deleted.
     *
     * @param connectionGroupID
     *     The identifier of the connection group to delete.
     *
     * @throws GuacamoleException
     *     If an error occurs while deleting the connection group.
     */
    @DELETE
    @Path("/{connectionGroupID}")
    public void deleteConnectionGroup(@QueryParam("token") String authToken, 
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("connectionGroupID") String connectionGroupID)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);
        
        // Get the connection group directory
        Directory<ConnectionGroup> connectionGroupDirectory = userContext.getConnectionGroupDirectory();

        // Delete the connection group
        connectionGroupDirectory.remove(connectionGroupID);

    }
    
    /**
     * Creates a new connection group and returns the new connection group,
     * with identifier field populated.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext in which the connection group is to be created.
     *
     * @param connectionGroup
     *     The connection group to create.
     * 
     * @return
     *     The new connection group.
     *
     * @throws GuacamoleException
     *     If an error occurs while creating the connection group.
     */
    @POST
    public APIConnectionGroup createConnectionGroup(
            @QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            APIConnectionGroup connectionGroup) throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);

        // Validate that connection group data was provided
        if (connectionGroup == null)
            throw new GuacamoleClientException("Connection group JSON must be submitted when creating connections groups.");

        // Add the new connection group
        Directory<ConnectionGroup> connectionGroupDirectory = userContext.getConnectionGroupDirectory();
        connectionGroupDirectory.add(new APIConnectionGroupWrapper(connectionGroup));

        // Return the new connection group
        return connectionGroup;

    }
    
    /**
     * Updates a connection group. If the parent identifier of the
     * connection group is changed, the connection group will also be moved to
     * the new parent group.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext containing the connection group to be updated.
     *
     * @param connectionGroupID
     *     The identifier of the existing connection group to update.
     *
     * @param connectionGroup
     *     The data to update the existing connection group with.
     *
     * @throws GuacamoleException
     *     If an error occurs while updating the connection group.
     */
    @PUT
    @Path("/{connectionGroupID}")
    public void updateConnectionGroup(@QueryParam("token") String authToken, 
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("connectionGroupID") String connectionGroupID,
            APIConnectionGroup connectionGroup)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);
        
        // Validate that connection group data was provided
        if (connectionGroup == null)
            throw new GuacamoleClientException("Connection group JSON must be submitted when updating connection groups.");

        // Get the connection group directory
        Directory<ConnectionGroup> connectionGroupDirectory = userContext.getConnectionGroupDirectory();

        // Retrieve connection group to update
        ConnectionGroup existingConnectionGroup = retrievalService.retrieveConnectionGroup(userContext, connectionGroupID);
        
        // Update the connection group
        existingConnectionGroup.setName(connectionGroup.getName());
        existingConnectionGroup.setParentIdentifier(connectionGroup.getParentIdentifier());
        existingConnectionGroup.setType(connectionGroup.getType());
        existingConnectionGroup.setAttributes(connectionGroup.getAttributes());
        connectionGroupDirectory.update(existingConnectionGroup);

    }
    
}

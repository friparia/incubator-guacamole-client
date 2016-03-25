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

package org.apache.guacamole.rest.user;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleResourceNotFoundException;
import org.apache.guacamole.GuacamoleSecurityException;
import org.apache.guacamole.net.auth.AuthenticationProvider;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.Directory;
import org.apache.guacamole.net.auth.User;
import org.apache.guacamole.net.auth.UserContext;
import org.apache.guacamole.net.auth.credentials.GuacamoleCredentialsException;
import org.apache.guacamole.net.auth.permission.ObjectPermission;
import org.apache.guacamole.net.auth.permission.ObjectPermissionSet;
import org.apache.guacamole.net.auth.permission.Permission;
import org.apache.guacamole.net.auth.permission.SystemPermission;
import org.apache.guacamole.net.auth.permission.SystemPermissionSet;
import org.apache.guacamole.GuacamoleSession;
import org.apache.guacamole.rest.APIPatch;
import static org.apache.guacamole.rest.APIPatch.Operation.add;
import static org.apache.guacamole.rest.APIPatch.Operation.remove;
import org.apache.guacamole.rest.ObjectRetrievalService;
import org.apache.guacamole.rest.PATCH;
import org.apache.guacamole.rest.auth.AuthenticationService;
import org.apache.guacamole.rest.permission.APIPermissionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A REST Service for handling user CRUD operations.
 * 
 * @author James Muehlner
 * @author Michael Jumper
 */
@Path("/data/{dataSource}/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserRESTService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(UserRESTService.class);

    /**
     * The prefix of any path within an operation of a JSON patch which
     * modifies the permissions of a user regarding a specific connection.
     */
    private static final String CONNECTION_PERMISSION_PATCH_PATH_PREFIX = "/connectionPermissions/";
    
    /**
     * The prefix of any path within an operation of a JSON patch which
     * modifies the permissions of a user regarding a specific connection group.
     */
    private static final String CONNECTION_GROUP_PERMISSION_PATCH_PATH_PREFIX = "/connectionGroupPermissions/";

    /**
     * The prefix of any path within an operation of a JSON patch which
     * modifies the permissions of a user regarding a specific active connection.
     */
    private static final String ACTIVE_CONNECTION_PERMISSION_PATCH_PATH_PREFIX = "/activeConnectionPermissions/";

    /**
     * The prefix of any path within an operation of a JSON patch which
     * modifies the permissions of a user regarding another, specific user.
     */
    private static final String USER_PERMISSION_PATCH_PATH_PREFIX = "/userPermissions/";

    /**
     * The path of any operation within a JSON patch which modifies the
     * permissions of a user regarding the entire system.
     */
    private static final String SYSTEM_PERMISSION_PATCH_PATH = "/systemPermissions";
    
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
     * Gets a list of users in the given data source (UserContext), filtering
     * the returned list by the given permission, if specified.
     *
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext from which the users are to be retrieved.
     *
     * @param permissions
     *     The set of permissions to filter with. A user must have one or more
     *     of these permissions for a user to appear in the result.
     *     If null, no filtering will be performed.
     *
     * @return
     *     A list of all visible users. If a permission was specified, this
     *     list will contain only those users for whom the current user has
     *     that permission.
     *
     * @throws GuacamoleException
     *     If an error is encountered while retrieving users.
     */
    @GET
    public List<APIUser> getUsers(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            @QueryParam("permission") List<ObjectPermission.Type> permissions)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);

        // An admin user has access to any user
        User self = userContext.self();
        SystemPermissionSet systemPermissions = self.getSystemPermissions();
        boolean isAdmin = systemPermissions.hasPermission(SystemPermission.Type.ADMINISTER);

        // Get the directory
        Directory<User> userDirectory = userContext.getUserDirectory();

        // Filter users, if requested
        Collection<String> userIdentifiers = userDirectory.getIdentifiers();
        if (!isAdmin && permissions != null && !permissions.isEmpty()) {
            ObjectPermissionSet userPermissions = self.getUserPermissions();
            userIdentifiers = userPermissions.getAccessibleObjects(permissions, userIdentifiers);
        }
            
        // Retrieve all users, converting to API users
        List<APIUser> apiUsers = new ArrayList<APIUser>();
        for (User user : userDirectory.getAll(userIdentifiers))
            apiUsers.add(new APIUser(user));

        return apiUsers;

    }
    
    /**
     * Retrieves an individual user.
     *
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext from which the requested user is to be retrieved.
     *
     * @param username
     *     The username of the user to retrieve.
     *
     * @return user
     *     The user having the given username.
     *
     * @throws GuacamoleException
     *     If an error occurs while retrieving the user.
     */
    @GET
    @Path("/{username}")
    public APIUser getUser(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("username") String username)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);

        // Retrieve the requested user
        User user = retrievalService.retrieveUser(session, authProviderIdentifier, username);
        return new APIUser(user);

    }
    
    /**
     * Creates a new user and returns the user that was created.
     *
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext in which the requested user is to be created.
     *
     * @param user
     *     The new user to create.
     *
     * @throws GuacamoleException
     *     If a problem is encountered while creating the user.
     *
     * @return
     *     The newly created user.
     */
    @POST
    public APIUser createUser(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier, APIUser user)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);

        // Get the directory
        Directory<User> userDirectory = userContext.getUserDirectory();
        
        // Randomly set the password if it wasn't provided
        if (user.getPassword() == null)
            user.setPassword(UUID.randomUUID().toString());

        // Create the user
        userDirectory.add(new APIUserWrapper(user));

        return user;

    }
    
    /**
     * Updates an individual existing user.
     *
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext in which the requested user is to be updated.
     *
     * @param username
     *     The username of the user to update.
     *
     * @param user
     *     The data to update the user with.
     *
     * @throws GuacamoleException
     *     If an error occurs while updating the user.
     */
    @PUT
    @Path("/{username}")
    public void updateUser(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("username") String username, APIUser user) 
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);

        // Get the directory
        Directory<User> userDirectory = userContext.getUserDirectory();

        // Validate data and path are sane
        if (!user.getUsername().equals(username))
            throw new GuacamoleClientException("Username in path does not match username provided JSON data.");
        
        // A user may not use this endpoint to modify himself
        if (userContext.self().getIdentifier().equals(user.getUsername()))
            throw new GuacamoleSecurityException("Permission denied.");

        // Get the user
        User existingUser = retrievalService.retrieveUser(userContext, username);

        // Do not update the user password if no password was provided
        if (user.getPassword() != null)
            existingUser.setPassword(user.getPassword());

        // Update user attributes
        existingUser.setAttributes(user.getAttributes());

        // Update the user
        userDirectory.update(existingUser);

    }
    
    /**
     * Updates the password for an individual existing user.
     *
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext in which the requested user is to be updated.
     *
     * @param username
     *     The username of the user to update.
     *
     * @param userPasswordUpdate
     *     The object containing the old password for the user, as well as the
     *     new password to set for that user.
     *
     * @param request
     *     The HttpServletRequest associated with the password update attempt.
     *
     * @throws GuacamoleException
     *     If an error occurs while updating the user's password.
     */
    @PUT
    @Path("/{username}/password")
    public void updatePassword(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("username") String username,
            APIUserPasswordUpdate userPasswordUpdate,
            @Context HttpServletRequest request) throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);

        // Build credentials
        Credentials credentials = new Credentials();
        credentials.setUsername(username);
        credentials.setPassword(userPasswordUpdate.getOldPassword());
        credentials.setRequest(request);
        credentials.setSession(request.getSession(true));
        
        // Verify that the old password was correct
        try {
            AuthenticationProvider authProvider = userContext.getAuthenticationProvider();
            if (authProvider.authenticateUser(credentials) == null)
                throw new GuacamoleSecurityException("Permission denied.");
        }

        // Pass through any credentials exceptions as simple permission denied
        catch (GuacamoleCredentialsException e) {
            throw new GuacamoleSecurityException("Permission denied.");
        }

        // Get the user directory
        Directory<User> userDirectory = userContext.getUserDirectory();
        
        // Get the user that we want to updates
        User user = retrievalService.retrieveUser(userContext, username);
        
        // Set password to the newly provided one
        user.setPassword(userPasswordUpdate.getNewPassword());
        
        // Update the user
        userDirectory.update(user);
        
    }
    
    /**
     * Deletes an individual existing user.
     *
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext from which the requested user is to be deleted.
     *
     * @param username
     *     The username of the user to delete.
     *
     * @throws GuacamoleException
     *     If an error occurs while deleting the user.
     */
    @DELETE
    @Path("/{username}")
    public void deleteUser(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("username") String username) 
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);

        // Get the directory
        Directory<User> userDirectory = userContext.getUserDirectory();

        // Get the user
        User existingUser = userDirectory.get(username);
        if (existingUser == null)
            throw new GuacamoleResourceNotFoundException("No such user: \"" + username + "\"");

        // Delete the user
        userDirectory.remove(username);

    }

    /**
     * Gets a list of permissions for the user with the given username.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext in which the requested user is to be found.
     *
     * @param username
     *     The username of the user to retrieve permissions for.
     *
     * @return
     *     A list of all permissions granted to the specified user.
     *
     * @throws GuacamoleException
     *     If an error occurs while retrieving permissions.
     */
    @GET
    @Path("/{username}/permissions")
    public APIPermissionSet getPermissions(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("username") String username) 
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);

        User user;

        // If username is own username, just use self - might not have query permissions
        if (userContext.self().getIdentifier().equals(username))
            user = userContext.self();

        // If not self, query corresponding user from directory
        else {
            user = userContext.getUserDirectory().get(username);
            if (user == null)
                throw new GuacamoleResourceNotFoundException("No such user: \"" + username + "\"");
        }

        return new APIPermissionSet(user);

    }

    /**
     * Updates the given permission set patch by queuing an add or remove
     * operation for the given permission based on the given patch operation.
     *
     * @param <PermissionType>
     *     The type of permission stored within the permission set.
     *
     * @param operation
     *     The patch operation to perform.
     *
     * @param permissionSetPatch
     *     The permission set patch being modified.
     *
     * @param permission
     *     The permission being added or removed from the set.
     *
     * @throws GuacamoleException
     *     If the requested patch operation is not supported.
     */
    private <PermissionType extends Permission> void updatePermissionSet(
            APIPatch.Operation operation,
            PermissionSetPatch<PermissionType> permissionSetPatch,
            PermissionType permission) throws GuacamoleException {

        // Add or remove permission based on operation
        switch (operation) {

            // Add permission
            case add:
                permissionSetPatch.addPermission(permission);
                break;

            // Remove permission
            case remove:
                permissionSetPatch.removePermission(permission);
                break;

            // Unsupported patch operation
            default:
                throw new GuacamoleClientException("Unsupported patch operation: \"" + operation + "\"");

        }

    }
    
    /**
     * Applies a given list of permission patches. Each patch specifies either
     * an "add" or a "remove" operation for a permission type, represented by
     * a string. Valid permission types depend on the path of each patch
     * operation, as the path dictates the permission being modified, such as
     * "/connectionPermissions/42" or "/systemPermissions".
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext in which the requested user is to be found.
     *
     * @param username
     *     The username of the user to modify the permissions of.
     *
     * @param patches
     *     The permission patches to apply for this request.
     *
     * @throws GuacamoleException
     *     If a problem is encountered while modifying permissions.
     */
    @PATCH
    @Path("/{username}/permissions")
    public void patchPermissions(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("username") String username,
            List<APIPatch<String>> patches) throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);

        // Get the user
        User user = userContext.getUserDirectory().get(username);
        if (user == null)
            throw new GuacamoleResourceNotFoundException("No such user: \"" + username + "\"");

        // Permission patches for all types of permissions
        PermissionSetPatch<ObjectPermission> connectionPermissionPatch       = new PermissionSetPatch<ObjectPermission>();
        PermissionSetPatch<ObjectPermission> connectionGroupPermissionPatch  = new PermissionSetPatch<ObjectPermission>();
        PermissionSetPatch<ObjectPermission> activeConnectionPermissionPatch = new PermissionSetPatch<ObjectPermission>();
        PermissionSetPatch<ObjectPermission> userPermissionPatch             = new PermissionSetPatch<ObjectPermission>();
        PermissionSetPatch<SystemPermission> systemPermissionPatch           = new PermissionSetPatch<SystemPermission>();
        
        // Apply all patch operations individually
        for (APIPatch<String> patch : patches) {

            String path = patch.getPath();

            // Create connection permission if path has connection prefix
            if (path.startsWith(CONNECTION_PERMISSION_PATCH_PATH_PREFIX)) {

                // Get identifier and type from patch operation
                String identifier = path.substring(CONNECTION_PERMISSION_PATCH_PATH_PREFIX.length());
                ObjectPermission.Type type = ObjectPermission.Type.valueOf(patch.getValue());

                // Create and update corresponding permission
                ObjectPermission permission = new ObjectPermission(type, identifier);
                updatePermissionSet(patch.getOp(), connectionPermissionPatch, permission);
                
            }

            // Create connection group permission if path has connection group prefix
            else if (path.startsWith(CONNECTION_GROUP_PERMISSION_PATCH_PATH_PREFIX)) {

                // Get identifier and type from patch operation
                String identifier = path.substring(CONNECTION_GROUP_PERMISSION_PATCH_PATH_PREFIX.length());
                ObjectPermission.Type type = ObjectPermission.Type.valueOf(patch.getValue());

                // Create and update corresponding permission
                ObjectPermission permission = new ObjectPermission(type, identifier);
                updatePermissionSet(patch.getOp(), connectionGroupPermissionPatch, permission);
                
            }

            // Create active connection permission if path has active connection prefix
            else if (path.startsWith(ACTIVE_CONNECTION_PERMISSION_PATCH_PATH_PREFIX)) {

                // Get identifier and type from patch operation
                String identifier = path.substring(ACTIVE_CONNECTION_PERMISSION_PATCH_PATH_PREFIX.length());
                ObjectPermission.Type type = ObjectPermission.Type.valueOf(patch.getValue());

                // Create and update corresponding permission
                ObjectPermission permission = new ObjectPermission(type, identifier);
                updatePermissionSet(patch.getOp(), activeConnectionPermissionPatch, permission);
                
            }

            // Create user permission if path has user prefix
            else if (path.startsWith(USER_PERMISSION_PATCH_PATH_PREFIX)) {

                // Get identifier and type from patch operation
                String identifier = path.substring(USER_PERMISSION_PATCH_PATH_PREFIX.length());
                ObjectPermission.Type type = ObjectPermission.Type.valueOf(patch.getValue());

                // Create and update corresponding permission
                ObjectPermission permission = new ObjectPermission(type, identifier);
                updatePermissionSet(patch.getOp(), userPermissionPatch, permission);

            }

            // Create system permission if path is system path
            else if (path.equals(SYSTEM_PERMISSION_PATCH_PATH)) {

                // Get identifier and type from patch operation
                SystemPermission.Type type = SystemPermission.Type.valueOf(patch.getValue());

                // Create and update corresponding permission
                SystemPermission permission = new SystemPermission(type);
                updatePermissionSet(patch.getOp(), systemPermissionPatch, permission);
                
            }

            // Otherwise, the path is not supported
            else
                throw new GuacamoleClientException("Unsupported patch path: \"" + path + "\"");

        } // end for each patch operation
        
        // Save the permission changes
        connectionPermissionPatch.apply(user.getConnectionPermissions());
        connectionGroupPermissionPatch.apply(user.getConnectionGroupPermissions());
        activeConnectionPermissionPatch.apply(user.getActiveConnectionPermissions());
        userPermissionPatch.apply(user.getUserPermissions());
        systemPermissionPatch.apply(user.getSystemPermissions());

    }

}

package com.ecommerce.user.service;

import com.ecommerce.user.configuration.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.springframework.stereotype.Service;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.web.client.RestClient;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.LoginResponse;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeycloakService {

    private final Keycloak keycloak;
    private final KeycloakProperties properties;
    private final RestClient restClient;

    public LoginResponse login(LoginRequest request) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("grant_type", "password");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("username", request.getEmail());
        form.add("password", request.getPassword());

        return restClient.post()
                .uri(properties.getServerUrl()
                        + "/realms/"
                        + properties.getRealm()
                        + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(LoginResponse.class);
    }

    public String createUser(String username,
                             String email,
                             String firstName,
                             String lastName) {

        RealmResource realm = keycloak.realm(properties.getRealm());

        UsersResource usersResource = realm.users();

        UserRepresentation user = new UserRepresentation();

        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        Response response = usersResource.create(user);

        if (response.getStatus() != 201) {

            System.out.println("Status = " + response.getStatus());

            String error = response.readEntity(String.class);

            System.out.println("Error = " + error);

            throw new RuntimeException("Failed to create user in Keycloak");
        }

        String location = response.getLocation().toString();

        return location.substring(location.lastIndexOf("/") + 1);
    }

    public void setPassword(String userId, String password) {

        CredentialRepresentation credential =
                new CredentialRepresentation();

        credential.setTemporary(false);
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);

        keycloak.realm(properties.getRealm())
                .users()
                .get(userId)
                .resetPassword(credential);
    }

    public void assignUserRole(String userId) {

        System.out.println("Step 1");

        RoleRepresentation role =
                keycloak.realm(properties.getRealm())
                        .roles()
                        .get("USER")
                        .toRepresentation();

        System.out.println("Step 2");

        keycloak.realm(properties.getRealm())
                .users()
                .get(userId)
                .roles()
                .realmLevel()
                .add(List.of(role));

        System.out.println("Step 3");
    }
}
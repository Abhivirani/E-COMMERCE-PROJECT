package com.ecommerce.user.service;

import com.ecommerce.user.dto.AddressDTO;
import com.ecommerce.user.dto.UserRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.model.Address;
import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;



@Service
@RequiredArgsConstructor
public class UserSevice {
    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    public Boolean validateUser(String userId)
    {
        return userRepository.existsById(userId);
    }

    public List<UserResponse> fetchAllUsers()
    {
        List<User> userList = userRepository.findAll();
        return userList.stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    public Optional<UserResponse> fetchUser(String id)
    {
//        for(User user: userList)
//        {
//            if(user.getId().equals(id))
//            {
//                return user;
//            }
//        }
//        return null;

//        return userList.stream()
//                .filter(user -> user.getId().equals(id))
//                .findFirst();
           return userRepository.findById(id)
                   .map(this::mapToUserResponse);
    }

    public void addUser(UserRequest userRequest)
    {
        // Step 1: Create user in Keycloak
        String keycloakUserId =
                keycloakService.createUser(
                        userRequest.getEmail(),
                        userRequest.getEmail(),
                        userRequest.getFirstName(),
                        userRequest.getLastName()
                );

        // Step 2: Set password
        keycloakService.setPassword(
                keycloakUserId,
                userRequest.getPassword()
        );

        // Step 3: Assign USER role
        keycloakService.assignUserRole(keycloakUserId);

        // Step 4: Save in PostgreSQL
        User user = new User();
        user.setId(keycloakUserId);
        updateUserFromRequest(user, userRequest);
        userRepository.save(user);
    }




    public boolean updateUser(String id,UserRequest updatedUserRequest)
    {
        return userRepository.findById(id)
                .map(existingUser ->{
                            updateUserFromRequest(existingUser,updatedUserRequest);
                            userRepository.save(existingUser);
                            return true;
                        }
                ).orElse(false);
    }

    private void updateUserFromRequest(User user, UserRequest userRequest) {
        user.setFirstName(userRequest.getFirstName());
        user.setLastName(userRequest.getLastName());
        user.setEmail(userRequest.getEmail());
        user.setPhone(userRequest.getPhone());

        if(userRequest.getAddress() != null)
        {
            Address address = new Address();
            address.setStreet(userRequest.getAddress().getStreet());
            address.setCity(userRequest.getAddress().getCity());
            address.setState(userRequest.getAddress().getState());
            address.setCountry(userRequest.getAddress().getCountry());
            address.setZipcode(userRequest.getAddress().getZipcode());
            user.setAddress(address);
        }
    }

    private UserResponse mapToUserResponse(User user){
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRole(user.getRole());

        if(user.getAddress() != null)
        {
            AddressDTO addressDTO = new AddressDTO();
            addressDTO.setStreet(user.getAddress().getStreet());
            addressDTO.setCity(user.getAddress().getCity());
            addressDTO.setCountry(user.getAddress().getCountry());
            addressDTO.setState(user.getAddress().getState());
            addressDTO.setCountry(user.getAddress().getCountry());
            addressDTO.setZipcode(user.getAddress().getZipcode());
            response.setAddress(addressDTO);
        }
        return response;
    }

}

package com.ecommerce.user.controller;
import com.ecommerce.user.dto.UserRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.service.UserSevice;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/users")
public class UserController {

    private final UserSevice userSevice;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers()
    {
        return new ResponseEntity<>(userSevice.fetchAllUsers(), HttpStatus.OK);
        //return ResponseEntity.ok(userSevice.fetchAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserResponse> getUser(@PathVariable String id)
    {
//        User user = userSevice.fetchUser(id);
//
//        if(user == null)
//        {
//            return ResponseEntity.notFound().build();
//        }
//
//        return ResponseEntity.ok(user);

        return userSevice.fetchUser(id)
                .map(ResponseEntity::ok)
                .orElseGet(()-> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<String> createUser(@RequestBody UserRequest userRequest)
    {
        userSevice.addUser(userRequest);
        return new ResponseEntity<>("User Added Successfully",HttpStatus.OK);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<String> updateUser(@PathVariable String id,@RequestBody UserRequest updateUserRequest)
    {
        boolean updated = userSevice.updateUser(id,updateUserRequest);

        if(updated) {
            return new ResponseEntity<>("User Updated Successfully", HttpStatus.OK);
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId)
    {
        return ResponseEntity.ok(userSevice.validateUser(userId));
    }
}

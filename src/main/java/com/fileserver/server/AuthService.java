package com.fileserver.server;

public class AuthService {

    private final UserStore userStore;

    public AuthService(UserStore userStore) {
        this.userStore = userStore;
    }

    public boolean authenticate(String username, String password) {
        return userStore.authenticate(username, password);
    }

    public String getRole(String username) {
        return userStore.getRole(username);
    }
}
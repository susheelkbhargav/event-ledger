package com.eventledger.gateway.client;

public class AccountServiceServerException extends RuntimeException {
    public AccountServiceServerException(int status) {
        super("Account Service returned " + status);
    }
}

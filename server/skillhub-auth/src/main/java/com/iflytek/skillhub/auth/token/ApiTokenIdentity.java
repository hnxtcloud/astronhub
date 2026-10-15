package com.iflytek.skillhub.auth.token;

/** Non-secret identity of the bearer credential; never contains the token bytes. */
public record ApiTokenIdentity(Long tokenId) implements java.io.Serializable { }

package ru.atmos.gravemarket.econ;

import java.util.UUID;

public interface Economy {
    long balance(UUID playerId);
    boolean withdraw(UUID playerId, long amount);
    void deposit(UUID playerId, long amount);
    String currencyName();
}

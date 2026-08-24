package io.github.legendaryforge.legendary.core.api.registry;

public interface RegistryAccess {

    <T> Registry<T> registry(RegistryKey<T> key);

    <T> Registrar<T> registrar(RegistryKey<T> key);
}

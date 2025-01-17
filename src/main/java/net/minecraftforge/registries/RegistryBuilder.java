/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.registries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry.*;

import javax.annotation.Nullable;

public class RegistryBuilder<T extends IForgeRegistryEntry<T>>
{
    private static final int MAX_ID = Integer.MAX_VALUE - 1;

    private ResourceLocation registryName;
    private Class<T> registryType;
    private ResourceLocation optionalDefaultKey;
    private int minId = 0;
    private int maxId = MAX_ID;
    private List<AddCallback<T>> addCallback = Lists.newArrayList();
    private List<ClearCallback<T>> clearCallback = Lists.newArrayList();
    private List<CreateCallback<T>> createCallback = Lists.newArrayList();
    private List<ValidateCallback<T>> validateCallback = Lists.newArrayList();
    private List<BakeCallback<T>> bakeCallback = Lists.newArrayList();
    private Function<T, Holder.Reference<T>> vanillaHolder;
    private boolean saveToDisc = true;
    private boolean sync = true;
    private boolean allowOverrides = true;
    private boolean allowModifications = false;
    private boolean hasWrapper = false;
    private Supplier<RegistryAccess.RegistryData<T>> dataPackRegistryData = () -> null; // If present, implies this is a datapack registry.
    private DummyFactory<T> dummyFactory;
    private MissingFactory<T> missingFactory;
    private Set<ResourceLocation> legacyNames = new HashSet<>();

    public RegistryBuilder<T> setName(ResourceLocation name)
    {
        this.registryName = name;
        return this;
    }

    public RegistryBuilder<T> setType(Class<T> type)
    {
        this.registryType = type;
        return this;
    }

    public RegistryBuilder<T> setIDRange(int min, int max)
    {
        this.minId = Math.max(min, 0);
        this.maxId = Math.min(max, MAX_ID);
        return this;
    }

    public RegistryBuilder<T> setMaxID(int max)
    {
        return this.setIDRange(0, max);
    }

    public RegistryBuilder<T> setDefaultKey(ResourceLocation key)
    {
        this.optionalDefaultKey = key;
        return this;
    }

    @SuppressWarnings("unchecked")
    public RegistryBuilder<T> addCallback(Object inst)
    {
        if (inst instanceof AddCallback)
            this.add((AddCallback<T>)inst);
        if (inst instanceof ClearCallback)
            this.add((ClearCallback<T>)inst);
        if (inst instanceof CreateCallback)
            this.add((CreateCallback<T>)inst);
        if (inst instanceof ValidateCallback)
            this.add((ValidateCallback<T>)inst);
        if (inst instanceof BakeCallback)
            this.add((BakeCallback<T>)inst);
        if (inst instanceof DummyFactory)
            this.set((DummyFactory<T>)inst);
        if (inst instanceof MissingFactory)
            this.set((MissingFactory<T>)inst);
        return this;
    }

    public RegistryBuilder<T> add(AddCallback<T> add)
    {
        this.addCallback.add(add);
        return this;
    }

    public RegistryBuilder<T> onAdd(AddCallback<T> add)
    {
        return this.add(add);
    }

    public RegistryBuilder<T> add(ClearCallback<T> clear)
    {
        this.clearCallback.add(clear);
        return this;
    }

    public RegistryBuilder<T> onClear(ClearCallback<T> clear)
    {
        return this.add(clear);
    }

    public RegistryBuilder<T> add(CreateCallback<T> create)
    {
        this.createCallback.add(create);
        return this;
    }

    public RegistryBuilder<T> onCreate(CreateCallback<T> create)
    {
        return this.add(create);
    }

    public RegistryBuilder<T> add(ValidateCallback<T> validate)
    {
        this.validateCallback.add(validate);
        return this;
    }

    public RegistryBuilder<T> onValidate(ValidateCallback<T> validate)
    {
        return this.add(validate);
    }

    public RegistryBuilder<T> add(BakeCallback<T> bake)
    {
        this.bakeCallback.add(bake);
        return this;
    }

    public RegistryBuilder<T> onBake(BakeCallback<T> bake)
    {
        return this.add(bake);
    }

    public RegistryBuilder<T> set(DummyFactory<T> factory)
    {
        this.dummyFactory = factory;
        return this;
    }

    public RegistryBuilder<T> dummy(DummyFactory<T> factory)
    {
        return this.set(factory);
    }

    public RegistryBuilder<T> set(MissingFactory<T> missing)
    {
        this.missingFactory = missing;
        return this;
    }

    public RegistryBuilder<T> missing(MissingFactory<T> missing)
    {
        return this.set(missing);
    }

    public RegistryBuilder<T> disableSaving()
    {
        this.saveToDisc = false;
        return this;
    }

    /**
     * Prevents the registry from being synced to clients. Does *not* affect datapack registries, datapack registries are unsynced by default
     * unless a non-null network codec is registered via {@link #dataPackRegistry(Codec, Codec)}
     * @return this
     */
    public RegistryBuilder<T> disableSync()
    {
        this.sync = false;
        return this;
    }

    public RegistryBuilder<T> disableOverrides()
    {
        this.allowOverrides = false;
        return this;
    }

    public RegistryBuilder<T> allowModification()
    {
        this.allowModifications = true;
        return this;
    }

    RegistryBuilder<T> hasWrapper()
    {
        this.hasWrapper = true;
        return this;
    }

    public RegistryBuilder<T> legacyName(String name)
    {
        return legacyName(new ResourceLocation(name));
    }

    public RegistryBuilder<T> legacyName(ResourceLocation name)
    {
        this.legacyNames.add(name);
        return this;
    }

    RegistryBuilder<T> vanillaHolder(Function<T, Holder.Reference<T>> func)
    {
        this.vanillaHolder = func;
        return this;
    }

    /**
     * Enables tags for this registry if not already.
     * All forge registries with wrappers inherently support tags.
     *
     * @return this builder
     * @see RegistryBuilder#hasWrapper()
     */
    public RegistryBuilder<T> hasTags()
    {
        // Tag system heavily relies on Registry<?> objects, so we need a wrapper for this registry to take advantage
        this.hasWrapper();
        return this;
    }

    /**
     * <p>Register this registry as an unsynced datapack registry, which will cause data to be loaded from
     * a datapack folder based on the registry's name. The mod that registers this registry does not need to exist
     * on the client to connect to servers with the mod/registry.</p>
     * <p>Data JSONs will be loaded from {@code data/<datapack_namespace>/modid/registryname/}, where modid is the mod that registered this registry.</p>
     *
     * @param codec the codec to be used for loading data from datapacks on servers
     * @return this builder
     *
     * @see #dataPackRegistry(Codec, Codec)
     */
    public RegistryBuilder<T> dataPackRegistry(Codec<T> codec)
    {
        return this.dataPackRegistry(codec, null);
    }

    /**
     * <p>Register this registry as a datapack registry, which will cause data to be loaded from
     * a datapack folder based on the registry's name.</p>
     * <p>Data JSONs will be loaded from {@code data/<datapack_namespace>/modid/registryname/}, where modid is the mod that registered this registry.</p>
     *
     * @param codec the codec to be used for loading data from datapacks on servers
     * @param networkCodec the codec to be used for syncing loaded data to clients.<br>
     * If networkCodec is null, data will not be synced, and clients without the mod that registered this registry can
     * connect to servers with the mod.<br>
     * If networkCodec is not null, then data will be synced (accessible via {@link ClientPacketListener#registryAccess()}),
     * and the mod must be present on a client to connect to servers with the mod.
     * @return this builder
     *
     * @see #dataPackRegistry(Codec)
     */
    public RegistryBuilder<T> dataPackRegistry(Codec<T> codec, @Nullable Codec<T> networkCodec)
    {
        this.hasWrapper(); // A wrapper is required for data pack registries.
        this.disableSync(); // Datapack registries are synced using a different system than static registries.
        // Supplier averts having to set the registry name before calling this.
        this.dataPackRegistryData = Suppliers.memoize(() -> {
            // Validate registry key.
            if (this.registryName == null)
                throw new IllegalStateException("Registry builder cannot build a datapack registry: registry name not set");

            ResourceKey<Registry<T>> registryKey = ResourceKey.createRegistryKey(this.registryName);
            return new RegistryAccess.RegistryData<>(registryKey, codec, networkCodec);
        });
        return this;
    }

    /**
     * Retrieves datapack registry information, if any.
     *
     * @return RegistryData containing the registry's key and codec(s). If returned data is null, this has not been marked as a datapack registry.
     *
     * @throws IllegalStateException if this has been marked as a datapack registry, but registry name has not been set.
     */
    @Nullable
    RegistryAccess.RegistryData<T> getDataPackRegistryData()
    {
        return this.dataPackRegistryData.get();
    }

    /**
     * Modders: Use {@link NewRegistryEvent#create(RegistryBuilder)} instead
     */
    IForgeRegistry<T> create()
    {
        if (hasWrapper)
        {
            if (getDefault() == null)
                addCallback(new NamespacedWrapper.Factory<T>());
            else
                addCallback(new NamespacedDefaultedWrapper.Factory<T>());
        }
        return RegistryManager.ACTIVE.createRegistry(registryName, this);
    }

    @Nullable
    public AddCallback<T> getAdd()
    {
        if (addCallback.isEmpty())
            return null;
        if (addCallback.size() == 1)
            return addCallback.get(0);

        return (owner, stage, id, obj, old) ->
        {
            for (AddCallback<T> cb : this.addCallback)
                cb.onAdd(owner, stage, id, obj, old);
        };
    }

    @Nullable
    public ClearCallback<T> getClear()
    {
        if (clearCallback.isEmpty())
            return null;
        if (clearCallback.size() == 1)
            return clearCallback.get(0);

        return (owner, stage) ->
        {
            for (ClearCallback<T> cb : this.clearCallback)
                cb.onClear(owner, stage);
        };
    }

    @Nullable
    public CreateCallback<T> getCreate()
    {
        if (createCallback.isEmpty())
            return null;
        if (createCallback.size() == 1)
            return createCallback.get(0);

        return (owner, stage) ->
        {
            for (CreateCallback<T> cb : this.createCallback)
                cb.onCreate(owner, stage);
        };
    }

    @Nullable
    public ValidateCallback<T> getValidate()
    {
        if (validateCallback.isEmpty())
            return null;
        if (validateCallback.size() == 1)
            return validateCallback.get(0);

        return (owner, stage, id, key, obj) ->
        {
            for (ValidateCallback<T> cb : this.validateCallback)
                cb.onValidate(owner, stage, id, key, obj);
        };
    }

    @Nullable
    public BakeCallback<T> getBake()
    {
        if (bakeCallback.isEmpty())
            return null;
        if (bakeCallback.size() == 1)
            return bakeCallback.get(0);

        return (owner, stage) ->
        {
            for (BakeCallback<T> cb : this.bakeCallback)
                cb.onBake(owner, stage);
        };
    }

    public Class<T> getType()
    {
        return registryType;
    }

    @Nullable
    public ResourceLocation getDefault()
    {
        return this.optionalDefaultKey;
    }

    public int getMinId()
    {
        return minId;
    }

    public int getMaxId()
    {
        return maxId;
    }

    public boolean getAllowOverrides()
    {
        return allowOverrides;
    }

    public boolean getAllowModifications()
    {
        return allowModifications;
    }

    @Nullable
    public DummyFactory<T> getDummyFactory()
    {
        return dummyFactory;
    }

    @Nullable
    public MissingFactory<T> getMissingFactory()
    {
        return missingFactory;
    }

    public boolean getSaveToDisc()
    {
        return saveToDisc;
    }

    public boolean getSync()
    {
        return sync;
    }

    public Set<ResourceLocation> getLegacyNames()
    {
        return legacyNames;
    }

    Function<T, Holder.Reference<T>> getVanillaHolder()
    {
        return this.vanillaHolder;
    }

    boolean getHasWrapper()
    {
        return this.hasWrapper;
    }
}
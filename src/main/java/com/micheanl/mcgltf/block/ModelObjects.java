package com.micheanl.mcgltf.block;

import com.micheanl.mcgltf.MCglTF;
import com.micheanl.mcgltf.entity.ModelEntity;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;

public final class ModelObjects {
	public static final String MODEL_BLOCK_ID = "model_block";
	public static final String MODEL_SOURCE_ID = "model_source";
	public static final String MODEL_ENTITY_ID = "model_entity";
	private static final float BLOCK_STRENGTH = 1.0f;
	private static final float ENTITY_SIZE = 1.0f;

	public static Block MODEL_BLOCK;
	public static Item MODEL_BLOCK_ITEM;
	public static BlockEntityType<ModelBlockEntity> MODEL_BLOCK_ENTITY;
	public static DataComponentType<String> MODEL_SOURCE;
	public static EntityType<ModelEntity> MODEL_ENTITY;

	private ModelObjects() {
	}

	public static void register() {
		MODEL_SOURCE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, MCglTF.id(MODEL_SOURCE_ID),
				DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, MCglTF.id(MODEL_BLOCK_ID));
		MODEL_BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				new ModelBlock(BlockBehaviour.Properties.of().strength(BLOCK_STRENGTH).noOcclusion().setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MCglTF.id(MODEL_BLOCK_ID));
		BlockItem item = new BlockItem(MODEL_BLOCK, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey));
		item.registerBlocks(Item.BY_BLOCK, item);
		MODEL_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, item);

		MODEL_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MCglTF.id(MODEL_BLOCK_ID),
				new BlockEntityType<>(ModelBlockEntity::new, Set.of(MODEL_BLOCK)));

		ResourceKey<EntityType<?>> entityKey = ResourceKey.create(Registries.ENTITY_TYPE, MCglTF.id(MODEL_ENTITY_ID));
		MODEL_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, entityKey,
				EntityType.Builder.of(ModelEntity::new, MobCategory.MISC).sized(ENTITY_SIZE, ENTITY_SIZE).build(entityKey));
	}
}

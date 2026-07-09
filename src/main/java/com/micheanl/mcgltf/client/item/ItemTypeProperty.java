package com.micheanl.mcgltf.client.item;

import com.micheanl.mcgltf.block.ModelObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public record ItemTypeProperty() implements SelectItemModelProperty<String> {
	public static final SelectItemModelProperty.Type<ItemTypeProperty, String> TYPE =
			SelectItemModelProperty.Type.create(MapCodec.unit(new ItemTypeProperty()), Codec.STRING);

	private static final String GLB = "glb";
	private static final String GLTF = "gltf";
	private static final String GLB_EXTENSION = ".glb";

	@Override
	public String get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext displayContext) {
		String source = stack.get(ModelObjects.MODEL_SOURCE);
		return source != null && source.toLowerCase(Locale.ROOT).endsWith(GLB_EXTENSION) ? GLB : GLTF;
	}

	@Override
	public Codec<String> valueCodec() {
		return Codec.STRING;
	}

	@Override
	public SelectItemModelProperty.Type<ItemTypeProperty, String> type() {
		return TYPE;
	}
}

package com.micheanl.mcgltf.client.item;

import com.micheanl.mcgltf.config.EditorConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public record ItemRenderProperty() implements SelectItemModelProperty<Boolean> {
	public static final SelectItemModelProperty.Type<ItemRenderProperty, Boolean> TYPE =
			SelectItemModelProperty.Type.create(MapCodec.unit(new ItemRenderProperty()), Codec.BOOL);

	@Override
	public Boolean get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext displayContext) {
		return EditorConfig.itemRender;
	}

	@Override
	public Codec<Boolean> valueCodec() {
		return Codec.BOOL;
	}

	@Override
	public SelectItemModelProperty.Type<ItemRenderProperty, Boolean> type() {
		return TYPE;
	}
}

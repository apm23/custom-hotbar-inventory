package com.anjas.custominventory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ModPayloads {
    private ModPayloads() {}

    public record CyclePage() implements CustomPacketPayload {
        public static final Type<CyclePage> TYPE = new Type<>(CustomHotbarInventory.id("cycle_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CyclePage> CODEC = StreamCodec.unit(new CyclePage());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SwapHotbar() implements CustomPacketPayload {
        public static final Type<SwapHotbar> TYPE = new Type<>(CustomHotbarInventory.id("swap_hotbar"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SwapHotbar> CODEC = StreamCodec.unit(new SwapHotbar());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SortAll() implements CustomPacketPayload {
        public static final Type<SortAll> TYPE = new Type<>(CustomHotbarInventory.id("sort_all"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SortAll> CODEC = StreamCodec.unit(new SortAll());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record MergeAll() implements CustomPacketPayload {
        public static final Type<MergeAll> TYPE = new Type<>(CustomHotbarInventory.id("merge_all"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MergeAll> CODEC = StreamCodec.unit(new MergeAll());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BrowseOpen() implements CustomPacketPayload {
        public static final Type<BrowseOpen> TYPE = new Type<>(CustomHotbarInventory.id("browse_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BrowseOpen> CODEC = StreamCodec.unit(new BrowseOpen());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BrowseClose() implements CustomPacketPayload {
        public static final Type<BrowseClose> TYPE = new Type<>(CustomHotbarInventory.id("browse_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BrowseClose> CODEC = StreamCodec.unit(new BrowseClose());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final class DirectPage {
        private DirectPage() {}
        public record P1() implements CustomPacketPayload { public static final Type<P1> TYPE=new Type<>(CustomHotbarInventory.id("page_1")); public static final StreamCodec<RegistryFriendlyByteBuf,P1> CODEC=StreamCodec.unit(new P1()); @Override public Type<? extends CustomPacketPayload> type(){return TYPE;} }
        public record P2() implements CustomPacketPayload { public static final Type<P2> TYPE=new Type<>(CustomHotbarInventory.id("page_2")); public static final StreamCodec<RegistryFriendlyByteBuf,P2> CODEC=StreamCodec.unit(new P2()); @Override public Type<? extends CustomPacketPayload> type(){return TYPE;} }
        public record P3() implements CustomPacketPayload { public static final Type<P3> TYPE=new Type<>(CustomHotbarInventory.id("page_3")); public static final StreamCodec<RegistryFriendlyByteBuf,P3> CODEC=StreamCodec.unit(new P3()); @Override public Type<? extends CustomPacketPayload> type(){return TYPE;} }
        public record P4() implements CustomPacketPayload { public static final Type<P4> TYPE=new Type<>(CustomHotbarInventory.id("page_4")); public static final StreamCodec<RegistryFriendlyByteBuf,P4> CODEC=StreamCodec.unit(new P4()); @Override public Type<? extends CustomPacketPayload> type(){return TYPE;} }
        public record P5() implements CustomPacketPayload { public static final Type<P5> TYPE=new Type<>(CustomHotbarInventory.id("page_5")); public static final StreamCodec<RegistryFriendlyByteBuf,P5> CODEC=StreamCodec.unit(new P5()); @Override public Type<? extends CustomPacketPayload> type(){return TYPE;} }
        public record P6() implements CustomPacketPayload { public static final Type<P6> TYPE=new Type<>(CustomHotbarInventory.id("page_6")); public static final StreamCodec<RegistryFriendlyByteBuf,P6> CODEC=StreamCodec.unit(new P6()); @Override public Type<? extends CustomPacketPayload> type(){return TYPE;} }
        public record P7() implements CustomPacketPayload { public static final Type<P7> TYPE=new Type<>(CustomHotbarInventory.id("page_7")); public static final StreamCodec<RegistryFriendlyByteBuf,P7> CODEC=StreamCodec.unit(new P7()); @Override public Type<? extends CustomPacketPayload> type(){return TYPE;} }
        public record P8() implements CustomPacketPayload { public static final Type<P8> TYPE=new Type<>(CustomHotbarInventory.id("page_8")); public static final StreamCodec<RegistryFriendlyByteBuf,P8> CODEC=StreamCodec.unit(new P8()); @Override public Type<? extends CustomPacketPayload> type(){return TYPE;} }
    }
}

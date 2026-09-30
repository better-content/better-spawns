package com.bettercontent.betterspawns.mixin;

import com.bettercontent.betterspawns.SpectatorDaylightPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Redirect(
            method = "tickTime",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"
            )
    )
    private void classSelector$pauseDaylightForSpectators(final ServerLevel level, final long newDayTime) {
        final List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        final int spectatorCount = (int) players.stream().filter(ServerPlayer::isSpectator).count();
        if (!SpectatorDaylightPolicy.shouldPause(players.size(), spectatorCount)) {
            level.setDayTime(newDayTime);
        }
    }
}

package de.melanx.skyblockbuilder.events;

import de.melanx.skyblockbuilder.util.Team;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.Event;

public abstract class SkyblockInvitationEvent extends Event {

    private final ServerPlayerEntity invitedPlayer;
    private final Team team;

    private SkyblockInvitationEvent(ServerPlayerEntity invitedPlayer, Team team) {
        this.invitedPlayer = invitedPlayer;
        this.team = team;
    }

    public ServerPlayerEntity getInvitedPlayer() {
        return this.invitedPlayer;
    }

    public Team getTeam() {
        return this.team;
    }

    @Override
    public boolean hasResult() {
        return true;
    }
    
    public static class Invite extends SkyblockInvitationEvent {

        private final ServerPlayerEntity invitor;
        
        public Invite(ServerPlayerEntity invitedPlayer, Team team, ServerPlayerEntity invitor) {
            super(invitedPlayer, team);
            this.invitor = invitor;
        }

        public ServerPlayerEntity getInvitor() {
            return this.invitor;
        }
    }
    
    public static class Accept extends SkyblockInvitationEvent {

        public Accept(ServerPlayerEntity invitedPlayer, Team team) {
            super(invitedPlayer, team);
        }
    }
}

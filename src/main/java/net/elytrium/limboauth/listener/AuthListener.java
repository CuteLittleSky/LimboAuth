/*
 * Copyright (C) 2021 - 2024 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limboauth.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.util.UuidUtils;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.floodgate.FloodgateApiHolder;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.elytrium.limboauth.model.UUIDType;
import re.imc.proxytransfercore.ProxyTransferCore;
import re.imc.proxytransfercore.listener.ConnectListener;
import re.imc.proxytransfercore.utils.ReflectionUtils;

import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// TODO: Customizable events priority
public class AuthListener {

  //private static final MethodHandle DELEGATE_FIELD;
  //private static final MethodHandle LOGIN_FIELD;

  private final LimboAuth plugin;
  private final Dao<RegisteredPlayer, String> playerDao;
  private final FloodgateApiHolder floodgateApi;

  /*
  private final Cache<String, String> loginFailurePlayers = CacheBuilder
          .newBuilder()
          .expireAfterWrite(Duration.of(20, ChronoUnit.SECONDS))
          .build();

   */

  public AuthListener(LimboAuth plugin, Dao<RegisteredPlayer, String> playerDao, FloodgateApiHolder floodgateApi) {
    this.plugin = plugin;
    this.playerDao = playerDao;
    this.floodgateApi = floodgateApi;
  }

  @Subscribe(order = PostOrder.FIRST)
  public void onPreLoginEvent(PreLoginEvent event) throws SQLException {
    InboundConnection connection = event.getConnection();
    if (ConnectListener.INITIAL_CONNECTION_DELEGATE != null) {
      connection = ReflectionUtils.getCastedValue(connection, ConnectListener.INITIAL_CONNECTION_DELEGATE);
    }

    MinecraftConnection mcConnection = (MinecraftConnection)ReflectionUtils.getValue(connection, ConnectListener.INITIAL_MINECRAFT_CONNECTION);
    if (ProxyTransferCore.getInstance().getTransferredConnectionsUUID().getIfPresent(mcConnection) != null) {
      return;
    }
    if (event.getUsername().toLowerCase().startsWith(Settings.IMP.MAIN.BEDROCK_PREFIX.toLowerCase())) {
      event.setResult(PreLoginEvent.PreLoginComponentResult.denied(plugin.getWrongNicknamePrefixKick()));
    }

    String virtualHostStr = event.getConnection().getVirtualHost().map(InetSocketAddress::getHostString)
            .map(str -> str.toLowerCase(Locale.ROOT))
            .orElse("");
    if (virtualHostStr.contains(Settings.IMP.MAIN.OFFLINE_HOST)) {
      event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
      return;
    }
    if (Settings.IMP.MAIN.ONLY_OFFLINE_MODE) {
      event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
    } else {
      if (!event.getUsername().startsWith("OF_")) {
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        /*
        String lastName = loginFailurePlayers.getIfPresent(event.getConnection().getRemoteAddress().getAddress().getHostAddress());

        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());

        if (lastName != null && lastName.equals(event.getUsername())) {
          Serializer serializer = LimboAuth.getSerializer();
          List<RegisteredPlayer> playerList = playerDao.queryForEq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, event.getUsername().toLowerCase(Locale.ROOT));
          if (playerList != null && playerList.size() > 0) {
            if (playerList.get(0).getUuidType() == UUIDType.JAVA_ONLINE) {
              event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
              return;
            }
          }

          event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
          // event.setResult(PreLoginEvent.PreLoginComponentResult.denied(serializer.deserialize(MessageFormat.format(Settings.IMP.MAIN.STRINGS.NOT_PREMIUM, event.getUsername()))));
          loginFailurePlayers.invalidate(event.getConnection().getRemoteAddress().getAddress().getHostAddress());
          return;
        }

        plugin.getServer().getScheduler()
                .buildTask(plugin, () -> {
                  if (!event.getConnection().isActive()) {
                    loginFailurePlayers.put(event.getConnection().getRemoteAddress().getAddress().getHostAddress(), event.getUsername());
                    plugin.getOnlineModeNames().remove(event.getUsername());
                  }
                })
                .delay(Duration.of(2, ChronoUnit.SECONDS))
                .schedule();

         */
      } else {
        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
      }
    }
  }

  // Temporarily disabled because some clients send UUID version 4 (random UUID) even if the player is cracked
  /*
  private boolean isPremiumByIdentifiedKey(InboundConnection inbound) throws Throwable {
    LoginInboundConnection inboundConnection = (LoginInboundConnection) inbound;
    InitialInboundConnection initialInbound = (InitialInboundConnection) DELEGATE_FIELD.invokeExact(inboundConnection);
    MinecraftConnection connection = initialInbound.getConnection();
    InitialLoginSessionHandler handler = (InitialLoginSessionHandler) connection.getSessionHandler();

    ServerLogin packet = (ServerLogin) LOGIN_FIELD.invokeExact(handler);
    if (packet == null) {
      return false;
    }

    UUID holder = packet.getHolderUuid();
    if (holder == null) {
      return false;
    }

    return holder.version() != 3;
  }
  */

  @Subscribe
  public void onProxyDisconnect(DisconnectEvent event) {
    this.plugin.unsetForcedPreviously(event.getPlayer().getUsername());
  }

  @Subscribe
  public void onPostLogin(PostLoginEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    Runnable postLoginTask = this.plugin.getPostLoginTasks().remove(uuid);
    if (postLoginTask != null) {
      // We need to delay for player's client to finish switching the server, it takes a little time.
      this.plugin.getServer().getScheduler()
          .buildTask(this.plugin, postLoginTask)
          .delay(Settings.IMP.MAIN.PREMIUM_AND_FLOODGATE_MESSAGES_DELAY, TimeUnit.MILLISECONDS)
          .schedule();
    }
  }

  @Subscribe
  public void onLoginLimboRegister(LoginLimboRegisterEvent event) {
    if (this.plugin.needAuth(event.getPlayer())) {
      event.addOnJoinCallback(() -> this.plugin.authPlayer(event.getPlayer()));
    }
  }

  @Subscribe(order = PostOrder.EARLY)
  public void onGameProfileRequest(GameProfileRequestEvent event) {
    if (ProxyTransferCore.getInstance().isTransferredPlayer(event.getGameProfile().getId())) {
      return;
    }
    if (!event.isOnlineMode()) {
      plugin.getOnlineModeNames().remove(event.getOriginalProfile().getName());
    }
    if (floodgateApi == null || !floodgateApi.isFloodgateUUID(event.getGameProfile().getId())) {
      if (!event.isOnlineMode() && !Settings.IMP.MAIN.OFFLINE_MODE_PREFIX.isEmpty()) {
        if (!event.getGameProfile().getName().startsWith(Settings.IMP.MAIN.OFFLINE_MODE_PREFIX)) {
          UUID newUuid = UuidUtils.generateOfflinePlayerUuid(Settings.IMP.MAIN.OFFLINE_MODE_PREFIX + event.getUsername());
          event.setGameProfile(event.getGameProfile().withName(Settings.IMP.MAIN.OFFLINE_MODE_PREFIX + event.getUsername()).withId(newUuid));
        }
      }
    }
    if (event.isOnlineMode() && !Settings.IMP.MAIN.ONLINE_MODE_PREFIX.isEmpty()) {
      event.setGameProfile(event.getGameProfile().withName(Settings.IMP.MAIN.ONLINE_MODE_PREFIX + event.getUsername()));
    }

    if (this.floodgateApi != null && this.floodgateApi.isFloodgateUUID(event.getGameProfile().getId())) {
      RegisteredPlayer registeredPlayer = AuthSessionHandler.fetchInfo(this.playerDao, event.getGameProfile().getId());
      if (registeredPlayer != null) {
        boolean needUpdate = false;
        String currentUuid = registeredPlayer.getUuid();

        if (!registeredPlayer.getNickname().equals(event.getGameProfile().getName())) {
          registeredPlayer.setNickname(event.getGameProfile().getName());
          needUpdate = true;
        }

        if (currentUuid.isEmpty()) {
          needUpdate = true;
        }

        if (needUpdate) {
          try {
            registeredPlayer.setUuid(event.getGameProfile().getId().toString());
            registeredPlayer.setUuidType(UUIDType.BEDROCK);
            this.playerDao.update(registeredPlayer);
          } catch (SQLException e) {
            throw new SQLRuntimeException(e);
          }
        } else {
          event.setGameProfile(event.getGameProfile().withId(UUID.fromString(currentUuid)));
        }


      }
    } else if (Settings.IMP.MAIN.SAVE_UUID) {
      RegisteredPlayer registeredPlayer = AuthSessionHandler.fetchInfo(this.playerDao, event.getGameProfile().getId());

      if (registeredPlayer != null) {
        boolean needUpdate = false;
        String currentUuid = registeredPlayer.getUuid();

        if (event.isOnlineMode() && !Objects.equals(registeredPlayer.getNickname(), event.getGameProfile().getName())) {
          registeredPlayer.setNickname(event.getGameProfile().getName());
          needUpdate = true;
        }

        if (currentUuid.isEmpty()) {
          needUpdate = true;
        }

        if (needUpdate) {
          try {
            registeredPlayer.setUuid(event.getGameProfile().getId().toString());
            registeredPlayer.setUuidType(event.isOnlineMode() ? UUIDType.JAVA_ONLINE : UUIDType.JAVA_OFFLINE);
            this.playerDao.update(registeredPlayer);
          } catch (SQLException e) {
            throw new SQLRuntimeException(e);
          }
        } else {
          event.setGameProfile(event.getGameProfile().withId(UUID.fromString(currentUuid)));
        }
      }
    } else if (event.isOnlineMode()) {
      try {
        UpdateBuilder<RegisteredPlayer, String> updateBuilder = this.playerDao.updateBuilder();
        updateBuilder.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, event.getUsername().toLowerCase(Locale.ROOT));
        updateBuilder.updateColumnValue(RegisteredPlayer.HASH_FIELD, "");
        updateBuilder.update();
      } catch (SQLException e) {
        throw new SQLRuntimeException(e);
      }
    }
  }

  /*
  static {
    try {
      DELEGATE_FIELD = MethodHandles.privateLookupIn(LoginInboundConnection.class, MethodHandles.lookup())
          .findGetter(LoginInboundConnection.class, "delegate", InitialInboundConnection.class);
      LOGIN_FIELD = MethodHandles.privateLookupIn(InitialLoginSessionHandler.class, MethodHandles.lookup())
          .findGetter(InitialLoginSessionHandler.class, "login", ServerLogin.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new ReflectionException(e);
    }
  }
  */
}

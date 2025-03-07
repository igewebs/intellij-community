// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io.webSocket;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.ide.HttpRequestHandler;
import org.jetbrains.io.BuiltInServer;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.io.jsonRpc.*;

import java.util.List;
import java.util.Map;

public abstract class WebSocketHandshakeHandler extends HttpRequestHandler implements ClientListener, ExceptionHandler {
  private static final Logger LOG = Logger.getInstance(WebSocketHandshakeHandler.class);

  private final NotNullLazyValue<ClientManager> clientManager = NotNullLazyValue.atomicLazy(() -> {
    ClientManager result = new ClientManager(this, this, null);
    Disposable serverDisposable = BuiltInServerManager.getInstance().getServerDisposable();
    assert serverDisposable != null;
    Disposer.register(serverDisposable, result);
    serverCreated(result);
    return result;
  });

  @Override
  public boolean isSupported(@NotNull FullHttpRequest request) {
    return request.method() == HttpMethod.GET &&
           "WebSocket".equalsIgnoreCase(request.headers().getAsString(HttpHeaderNames.UPGRADE)) &&
           request.uri().length() > 2;
  }

  protected void serverCreated(@NotNull ClientManager server) {
  }

  @Override
  public void exceptionCaught(@NotNull Throwable e) {
    NettyUtil.log(e, LOG);
  }

  @Override
  public boolean process(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) {
    handleWebSocketRequest(context, request, urlDecoder);
    return true;
  }

  private void handleWebSocketRequest(final @NotNull ChannelHandlerContext context, @NotNull FullHttpRequest request, final @NotNull QueryStringDecoder uriDecoder) {
    WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory("ws://" + request.headers().getAsString(HttpHeaderNames.HOST) + uriDecoder.path(), null, false, NettyUtil.MAX_CONTENT_LENGTH); //NON-NLS
    WebSocketServerHandshaker handshaker = factory.newHandshaker(request);
    if (handshaker == null) {
      WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(context.channel());
      return;
    }

    if (!context.channel().isOpen()) {
      return;
    }

    final Client client = new WebSocketClient(context.channel(), handshaker);
    context.channel().attr(ClientManagerKt.getCLIENT()).set(client);
    handshaker.handshake(context.channel(), request).addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) {
        if (future.isSuccess()) {
          ClientManager clientManager = WebSocketHandshakeHandler.this.clientManager.getValue();
          clientManager.addClient(client);
          MessageChannelHandler messageChannelHandler = new MessageChannelHandler(clientManager, getMessageServer());
          BuiltInServer.Companion.replaceDefaultHandler(context, messageChannelHandler);
          ChannelHandlerContext messageChannelHandlerContext = context.pipeline().context(messageChannelHandler);
          context.pipeline().addBefore(messageChannelHandlerContext.name(), "webSocketFrameAggregator", new WebSocketFrameAggregator(NettyUtil.MAX_CONTENT_LENGTH));
          messageChannelHandlerContext.channel().attr(ClientManagerKt.getCLIENT()).set(client);
          connected(client, uriDecoder.parameters());
        }
      }
    });
  }

  protected abstract @NotNull MessageServer getMessageServer();

  @Override
  public void connected(@NotNull Client client, @Nullable Map<String, List<String>> parameters) {
  }
}
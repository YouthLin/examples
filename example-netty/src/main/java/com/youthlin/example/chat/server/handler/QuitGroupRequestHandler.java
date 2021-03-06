package com.youthlin.example.chat.server.handler;

import com.youthlin.example.chat.model.User;
import com.youthlin.example.chat.protocol.request.QuitGroupRequestPacket;
import com.youthlin.example.chat.util.SessionUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建: youthlin.chen
 * 时间: 2018-11-14 10:14
 */
@ChannelHandler.Sharable
public class QuitGroupRequestHandler extends SimpleChannelInboundHandler<QuitGroupRequestPacket> {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuitGroupRequestHandler.class);
    public static final QuitGroupRequestHandler INSTANCE = new QuitGroupRequestHandler();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, QuitGroupRequestPacket msg) throws Exception {
        Channel channel = ctx.channel();
        long groupId = msg.getGroupId();
        ChannelGroup group = SessionUtil.getChannelGroup(groupId);
        if (group == null) {
            LOGGER.error("群聊不存在");
        } else {
            User quitUser = SessionUtil.getUser(channel);
            msg.setWhoQuit(quitUser);
            group.remove(channel);
            List<User> userList = new ArrayList<>();
            msg.setUserList(userList);
            if (!group.isEmpty()) {
                group.forEach(ch -> userList.add(SessionUtil.getUser(ch)));
                group.writeAndFlush(msg);
            } else {
                SessionUtil.clearGroup(groupId);
            }
            channel.writeAndFlush(msg);
        }
    }
}

package com.youthlin.example.chat.protocol.response;

import com.youthlin.example.chat.model.User;
import com.youthlin.example.chat.protocol.Command;
import com.youthlin.example.chat.protocol.Packet;
import lombok.Data;

/**
 * 创建: youthlin.chen
 * 时间: 2018-11-12 16:38
 */
@Data
public class LoginResponsePacket extends Packet {
    private static final long serialVersionUID = -3052711500867959575L;
    private boolean success;
    private User user;
    private String failReason;

    @Override
    public byte command() {
        return Command.LOGIN_RESPONSE;
    }
}

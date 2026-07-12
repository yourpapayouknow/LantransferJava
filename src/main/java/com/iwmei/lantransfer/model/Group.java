package com.iwmei.lantransfer.model;

import java.util.List;

// 本地传输分组数据对象，保存组名、默认口令和成员快照
public record Group(String name, String code, List<UserDevice> members) {
    // 清洗分组字段并固定成员快照
    public Group {
        name = UserDevice.cleanGroupName(name);
        code = code == null ? "" : code.trim();
        members = members == null ? List.of() : List.copyOf(members);
    }

    // 返回分组成员数量
    public int size() {
        return members.size();
    }

    // 构造可放入近期传输对象队列的组目标
    public UserDevice target() {
        return UserDevice.group(name, size(), code);
    }
}

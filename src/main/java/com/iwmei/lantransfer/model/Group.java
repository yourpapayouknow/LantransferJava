package com.iwmei.lantransfer.model;
import java.util.List;
public record Group(String name, String code, List<UserDevice> members) {
    public Group {
        name = UserDevice.cleanGroupName(name);
        code = code == null ? "" : code.trim();
        members = members == null ? List.of() : List.copyOf(members);
    }
    public int size() {
        return members.size();
    }
    public UserDevice target() {
        return UserDevice.group(name, size(), code);
    }
}

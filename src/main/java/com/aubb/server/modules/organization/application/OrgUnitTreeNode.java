package com.aubb.server.modules.organization.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class OrgUnitTreeNode {

    private final Long id;
    private final Long parentId;
    private final String code;
    private final String name;
    private final String type;
    private final int level;
    private final int sortOrder;
    private final String status;
    private final OffsetDateTime createdAt;
    private final List<OrgUnitTreeNode> children = new ArrayList<>();

    public OrgUnitTreeNode(
            Long id,
            Long parentId,
            String code,
            String name,
            String type,
            int level,
            int sortOrder,
            String status,
            OffsetDateTime createdAt) {
        this.id = id;
        this.parentId = parentId;
        this.code = code;
        this.name = name;
        this.type = type;
        this.level = level;
        this.sortOrder = sortOrder;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void addChild(OrgUnitTreeNode child) {
        children.add(child);
    }
}

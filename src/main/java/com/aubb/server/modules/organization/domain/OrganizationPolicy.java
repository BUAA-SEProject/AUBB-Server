package com.aubb.server.modules.organization.domain;

public class OrganizationPolicy {

    public OrganizationValidationResult validateRoot(OrgUnitType rootType) {
        if (rootType != OrgUnitType.SCHOOL) {
            return OrganizationValidationResult.rejected("根节点只能是 SCHOOL");
        }
        return OrganizationValidationResult.allowed(1);
    }

    public OrganizationValidationResult validateChild(OrgUnitType parentType, OrgUnitType childType, int parentLevel) {
        OrgUnitType expectedChildType =
                switch (parentType) {
                    case SCHOOL -> OrgUnitType.COLLEGE;
                    case COLLEGE -> OrgUnitType.COURSE;
                    case COURSE -> OrgUnitType.CLASS;
                    case CLASS -> null;
                };

        if (expectedChildType == null) {
            return OrganizationValidationResult.rejected("CLASS 节点不能再创建下级节点");
        }
        if (childType != expectedChildType) {
            return OrganizationValidationResult.rejected(
                    parentType.name() + " 下只能创建 " + expectedChildType.name() + " 节点");
        }
        return OrganizationValidationResult.allowed(parentLevel + 1);
    }
}

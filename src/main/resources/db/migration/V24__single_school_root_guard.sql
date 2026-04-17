ALTER TABLE org_units
    ADD CONSTRAINT ck_org_units_root_is_school
    CHECK (parent_id IS NOT NULL OR (type = 'SCHOOL' AND level = 1));

CREATE UNIQUE INDEX ux_org_units_single_school_root
    ON org_units ((true))
    WHERE parent_id IS NULL AND type = 'SCHOOL';

-- 进一步放宽 legacy role binding 的激活容忍窗口，覆盖应用/数据库时钟抖动
-- 与成员同步后跨请求立即鉴权的常见生产路径，避免刚创建完成就误拒为 DENY_NO_ROLE_BINDING。

CREATE OR REPLACE FUNCTION legacy_binding_effective_from(
    binding_from TIMESTAMPTZ,
    binding_status TEXT
) RETURNS TIMESTAMPTZ AS $$
DECLARE
    activation_cutoff TIMESTAMPTZ;
BEGIN
    IF binding_from IS NULL THEN
        RETURN NULL;
    END IF;

    IF binding_status IS DISTINCT FROM 'ACTIVE' THEN
        RETURN binding_from;
    END IF;

    activation_cutoff := clock_timestamp() - interval '3 seconds';
    IF binding_from > activation_cutoff THEN
        RETURN activation_cutoff;
    END IF;

    RETURN binding_from;
END;
$$ LANGUAGE plpgsql;

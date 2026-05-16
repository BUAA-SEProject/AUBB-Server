-- Simplify gradebook: remove snapshots, appeals, and weighted scores

-- 1. Drop snapshot tables (grade_publish_snapshots has FK to batches, drop child first)
DROP TABLE IF EXISTS grade_publish_snapshots;
DROP TABLE IF EXISTS grade_publish_snapshot_batches;

-- 2. Drop appeal table
DROP TABLE IF EXISTS grade_appeals;

-- 3. Remove grade_weight column from assignments
ALTER TABLE assignments DROP CONSTRAINT IF EXISTS chk_assignments_grade_weight_positive;
ALTER TABLE assignments DROP COLUMN IF EXISTS grade_weight;

-- 4. Clean up appeal permissions
DELETE FROM builtin_role_permissions WHERE permission_code IN ('appeal.read.own', 'appeal.read.class', 'appeal.review');
DELETE FROM permissions WHERE code IN ('appeal.read.own', 'appeal.read.class', 'appeal.review', 'appeal.create', 'appeal.read');

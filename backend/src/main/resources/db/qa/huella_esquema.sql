SELECT md5(COALESCE(string_agg(objeto, E'\n' ORDER BY objeto), ''))
FROM (
    SELECT json_build_array('tabla', c.relname, c.relkind, c.relrowsecurity, c.relforcerowsecurity)::text AS objeto
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
      AND c.relname <> 'flyway_schema_history'
    UNION ALL
    SELECT json_build_array('columna', c.relname, a.attname,
        format_type(a.atttypid, a.atttypmod), a.attnotnull,
        pg_get_expr(d.adbin, d.adrelid), a.attidentity, a.attgenerated)::text
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = c.oid
    LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
      AND c.relname <> 'flyway_schema_history' AND a.attnum > 0 AND NOT a.attisdropped
    UNION ALL
    SELECT json_build_array('restriccion', c.relname, k.conname,
        pg_get_constraintdef(k.oid, true))::text
    FROM pg_constraint k JOIN pg_class c ON c.oid = k.conrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname <> 'flyway_schema_history'
    UNION ALL
    SELECT json_build_array('indice', t.relname, i.relname, pg_get_indexdef(i.oid))::text
    FROM pg_index x JOIN pg_class i ON i.oid = x.indexrelid
    JOIN pg_class t ON t.oid = x.indrelid JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE n.nspname = 'public' AND t.relname <> 'flyway_schema_history'
    UNION ALL
    SELECT json_build_array('secuencia', sequencename, data_type,
        start_value, min_value, max_value, increment_by, cycle, cache_size)::text
    FROM pg_sequences WHERE schemaname = 'public'
    UNION ALL
    SELECT json_build_array('vista', c.relname, pg_get_viewdef(c.oid, true))::text
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('v', 'm')
    UNION ALL
    SELECT json_build_array('politica', tablename, policyname, permissive, roles, cmd, qual, with_check)::text
    FROM pg_policies WHERE schemaname = 'public'
    UNION ALL
    SELECT json_build_array('trigger', c.relname, t.tgname, pg_get_triggerdef(t.oid, true))::text
    FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND NOT t.tgisinternal
) AS objetos;

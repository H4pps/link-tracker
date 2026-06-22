package com.linktracker.scrapper.infrastructure.memory.sql;

import com.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import java.util.ArrayList;
import java.util.List;

final class SqlPagination {

    private SqlPagination() {}

    static String apply(String sql, RepositoryPageRequest pageRequest) {
        StringBuilder builder = new StringBuilder(sql);
        if (pageRequest.bounded()) {
            builder.append("\nLIMIT ?");
        }
        if (pageRequest.offset() > 0) {
            builder.append("\nOFFSET ?");
        }
        return builder.toString();
    }

    static List<Object> arguments(RepositoryPageRequest pageRequest) {
        List<Object> arguments = new ArrayList<>();
        if (pageRequest.bounded()) {
            arguments.add(pageRequest.limit());
        }
        if (pageRequest.offset() > 0) {
            arguments.add(pageRequest.offset());
        }
        return arguments;
    }
}

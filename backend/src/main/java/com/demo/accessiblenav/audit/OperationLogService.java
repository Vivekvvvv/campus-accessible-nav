package com.demo.accessiblenav.audit;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class OperationLogService {

    private final OperationLogRepository repository;

    public OperationLogService(OperationLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OperationLog log(String action, String detail) {
        OperationLog log = new OperationLog();
        log.setActor(currentUsername());
        log.setActorRole(currentRole());
        log.setAction(action);
        log.setDetail(detail);
        log.setCreatedAt(Instant.now());
        return repository.save(log);
    }

    @Transactional(readOnly = true)
    public List<OperationLog> latest(int limit) {
        return search(null, null, null, null, null, limit);
    }

    @Transactional(readOnly = true)
    public List<OperationLog> search(String actor, String role, String action, Instant startAt, Instant endAt, Integer limit) {
        int size = limit == null ? 100 : Math.max(1, Math.min(2000, limit));
        List<OperationLog> all = repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        String actorKey = normalize(actor);
        String roleKey = normalize(role);
        String actionKey = normalize(action);
        return all.stream()
                .filter(log -> actorKey == null || containsIgnoreCase(log.getActor(), actorKey))
                .filter(log -> roleKey == null || containsIgnoreCase(log.getActorRole(), roleKey))
                .filter(log -> actionKey == null || containsIgnoreCase(log.getAction(), actionKey))
                .filter(log -> startAt == null || (log.getCreatedAt() != null && !log.getCreatedAt().isBefore(startAt)))
                .filter(log -> endAt == null || (log.getCreatedAt() != null && !log.getCreatedAt().isAfter(endAt)))
                .limit(size)
                .collect(Collectors.toList());
    }

    public String exportCsv(List<OperationLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,actor,actorRole,action,detail,createdAt\n");
        for (OperationLog log : logs) {
            sb.append(csv(log.getId()))
              .append(',')
              .append(csv(log.getActor()))
              .append(',')
              .append(csv(log.getActorRole()))
              .append(',')
              .append(csv(log.getAction()))
              .append(',')
              .append(csv(log.getDetail()))
              .append(',')
              .append(csv(log.getCreatedAt() == null ? null : log.getCreatedAt().toString()))
              .append('\n');
        }
        return sb.toString();
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return "system";
        }
        return auth.getName();
    }

    private String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String authority = ga == null ? null : ga.getAuthority();
            if (authority == null) {
                continue;
            }
            if (authority.startsWith("ROLE_")) {
                return authority.substring(5);
            }
        }
        return null;
    }

    private String normalize(String v) {
        String s = v == null ? null : v.trim();
        return s == null || s.isEmpty() ? null : s;
    }

    private boolean containsIgnoreCase(String source, String needle) {
        if (source == null || needle == null) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String csv(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        String escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}

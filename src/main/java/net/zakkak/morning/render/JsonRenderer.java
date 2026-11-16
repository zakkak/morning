package net.zakkak.morning.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import net.zakkak.morning.prs.CheckFailure;
import net.zakkak.morning.prs.MyPrsReport;
import net.zakkak.morning.prs.PullRequestNode;
import net.zakkak.morning.prs.PullRequestStatus;

/** {@code --format json}: schema {@code morning.my-prs/1} (contracts/output-json.md). */
public final class JsonRenderer {

    public static final String SCHEMA = "morning.my-prs/1";
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private JsonRenderer() {
    }

    public static String render(MyPrsReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", SCHEMA);
        root.put("section", report.section());
        root.put("generatedAt", report.generatedAt().toString());
        List<Object> prs = new ArrayList<>();
        for (PullRequestStatus s : report.prs()) {
            PullRequestNode pr = s.pr();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("repo", pr.repo());
            p.put("number", pr.number());
            p.put("title", pr.title());
            p.put("url", pr.url());
            p.put("draft", pr.isDraft());
            p.put("group", s.group().name());
            p.put("reviewDecision", pr.reviewDecision() == null ? null : pr.reviewDecision().name());
            p.put("mergeable", pr.mergeable().name());
            p.put("ci", pr.rollupState() == null ? null : pr.rollupState().name());
            List<Object> failures = new ArrayList<>();
            for (CheckFailure f : s.failures()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("check", f.check().name());
                m.put("url", f.check().url());
                m.put("class", f.failureClass().name());
                m.put("evidence", f.evidence());
                failures.add(m);
            }
            p.put("failures", failures);
            p.put("actions", s.actions().stream().map(Enum::name).toList());
            p.put("updatedAt", pr.updatedAt().toString());
            prs.add(p);
        }
        root.put("prs", prs);
        root.put("apiRequests", report.apiRequests());
        root.put("warnings", report.warnings());
        try {
            return JSON.writeValueAsString(root) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

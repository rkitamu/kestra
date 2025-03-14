package io.kestra.core.repositories;

import io.kestra.core.models.SearchResult;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.micronaut.data.model.Pageable;

import javax.annotation.Nullable;
import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FlowRepositoryInterface {

    Optional<Flow> findById(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDeleted);

    default Optional<Flow> findById(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return this.findById(tenantId, namespace, id, revision, false);
    }

    default Flow findByExecution(Execution execution) {
        Optional<Flow> find = this.findById(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            Optional.of(execution.getFlowRevision())
        );

        if (find.isEmpty()) {
            throw new IllegalStateException("Unable to find flow '" + execution.getNamespace() + "." +
                execution.getFlowId() + "' with revision " + execution.getFlowRevision() + " on execution " +
                execution.getId()
            );
        } else {
            return find.get();
        }
    }

    default Optional<Flow> findById(String tenantId, String namespace, String id) {
        return this.findById(tenantId, namespace, id, Optional.empty(), false);
    }

    Optional<FlowWithSource> findByIdWithSource(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDelete);

    default Optional<FlowWithSource> findByIdWithSource(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return this.findByIdWithSource(tenantId, namespace, id, revision, false);
    }

    default Optional<FlowWithSource> findByIdWithSource(String tenantId, String namespace, String id) {
        return this.findByIdWithSource(tenantId, namespace, id, Optional.empty(), false);
    }

    List<FlowWithSource> findRevisions(String tenantId, String namespace, String id);

    List<Flow> findAll(String tenantId);

    List<Flow> findAllForAllTenants();

    List<Flow> findByNamespace(String tenantId, String namespace);

    ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable Map<String, String> labels
    );

    List<FlowWithSource> findWithSource(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable Map<String, String> labels
    );

    ArrayListTotal<SearchResult<Flow>> findSourceCode(Pageable pageable, @Nullable String query, @Nullable String tenantId, @Nullable String namespace);

    List<String> findDistinctNamespace(String tenantId);

    FlowWithSource create(Flow flow, String flowSource, Flow flowWithDefaults);

    FlowWithSource update(Flow flow, Flow previous, String flowSource, Flow flowWithDefaults) throws ConstraintViolationException;

    Flow delete(Flow flow);
}

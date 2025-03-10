<template>
    <el-card>
        <div class="vueflow">
            <low-code-editor
                :key="execution.id"
                v-if="execution && flowGraph && flow?.source"
                :flow-id="execution.flowId"
                :namespace="execution.namespace"
                :flow-graph="flowGraph"
                :source="flow?.source"
                :execution="execution"
                :expanded-subflows="expandedSubflows"
                is-read-only
                @follow="forwardEvent('follow', $event)"
                view-type="topology"
                @expand-subflow="onExpandSubflow($event)"
            />
            <el-alert v-else type="warning" :closable="false">
                {{ $t("unable to generate graph")}}
            </el-alert>
        </div>
    </el-card>
</template>
<script>
    import LowCodeEditor from "../inputs/LowCodeEditor.vue";
    import {mapGetters, mapState} from "vuex";
    import {CLUSTER_PREFIX} from "@kestra-io/ui-libs/src/utils/constants";
    import Utils from "@kestra-io/ui-libs/src/utils/Utils";
    import STATE from "../../utils/state";
    export default {
        components: {
            LowCodeEditor
        },
        computed: {
            ...mapState("flow", ["flow","flowGraph"]),
            ...mapState("execution", ["execution"]),
            ...mapGetters("execution", ["subflowsExecutions"])
        },
        data() {
            return {
                previousExecutionId: undefined,
                expandedSubflows: [],
                sseBySubflow: {}
            };
        },
        watch: {
            execution() {
                this.loadData();
            }
        },
        mounted() {
            this.loadData();
        },
        unmounted() {
            Object.keys(this.sseBySubflow).forEach(this.closeSSE);
        },
        methods: {
            closeSSE(subflow) {
                this.sseBySubflow[subflow].close();
                delete this.sseBySubflow[subflow];
                this.$store.commit("execution/removeSubflowExecution", subflow)
            },
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            loadData(){
                this.loadGraph();
            },
            isUnused: function (nodeByUid, nodeUid) {
                let nodeToCheck = nodeByUid[nodeUid];

                if(!nodeToCheck) {
                    return false;
                }

                if(!nodeToCheck.task) {
                    // check if parent is unused (current node is probably a cluster root or end)
                    const splitUid = nodeToCheck.uid.split(".");
                    splitUid.pop();
                    return this.isUnused(nodeByUid, splitUid.join("."));
                }

                if (!nodeToCheck.executionId) {
                    return true;
                }

                const nodeExecution = nodeToCheck.executionId === this.execution?.id ? this.execution
                    : Object.values(this.subflowsExecutions).filter(execution => execution.id === nodeToCheck.executionId)?.[0];

                if (!nodeExecution) {
                    return true;
                }

                return !nodeExecution.taskRunList.some(taskRun => taskRun.taskId === nodeToCheck.task?.id);


            },
            loadGraph(force) {
                if (this.execution && (force || (this.flowGraph === undefined || this.previousExecutionId !== this.execution.id))) {
                    this.previousExecutionId = this.execution.id;
                    this.$store.dispatch("flow/loadGraph", {
                        flow: {
                            namespace: this.execution.namespace,
                            id: this.execution.flowId,
                            revision: this.execution.flowRevision
                        },
                        params: {
                            subflows: this.expandedSubflows
                        }
                    }).then(() => {
                        const subflowPaths = this.flowGraph.clusters
                            ?.map(c => c.cluster)
                            ?.filter(cluster => cluster.type.endsWith("SubflowGraphCluster"))
                            ?.map(cluster => cluster.uid.replace(CLUSTER_PREFIX, ""))
                            ?? [];
                        const nodeByUid = {};

                        this.flowGraph.nodes
                            // lowest depth first to be available in nodeByUid map for child-to-parent unused check
                            .sort((a, b) => a.uid.length - b.uid.length)
                            .forEach(node => {
                                nodeByUid[node.uid] = node;

                                const parentSubflow = subflowPaths.filter(subflowPath => node.uid.startsWith(subflowPath + "."))
                                    .sort((a, b) => b.length - a.length)?.[0]

                                if(parentSubflow) {
                                    if(parentSubflow in this.subflowsExecutions) {
                                        node.executionId = this.subflowsExecutions[parentSubflow].id;
                                    }

                                    return;
                                }

                                node.executionId = this.execution.id;

                                // reduce opacity for cluster root & end
                                if(!node.task && this.isUnused(nodeByUid, node.uid)) {
                                    node.unused = true;
                                }
                            });

                        this.flowGraph.edges
                            // keep only unused (or skipped) paths
                            .filter(edge => {
                                return this.isUnused(nodeByUid, edge.target) || this.isUnused(nodeByUid, edge.source);
                            }).forEach(edge => edge.unused = true);

                        // force refresh
                        this.$store.commit("flow/setFlowGraph", Object.assign({}, this.flowGraph));
                    })
                }
            },
            onExpandSubflow(expandedSubflows) {
                this.expandedSubflows = expandedSubflows;

                this.handleSubflowsSSE();
            },
            handleSubflowsSSE() {
                Object.keys(this.sseBySubflow).filter(subflow => !this.expandedSubflows.includes(subflow))
                    .forEach(this.closeSSE);

                // resolve parent subflows' execution first
                const subflowsWithoutSSE = this.expandedSubflows.filter(subflow => !(subflow in this.sseBySubflow))
                    .sort((a, b) => (a.match(/\./g)?.length || 0) - (b.match(/\./g)?.length || 0));


                subflowsWithoutSSE.forEach(subflow => {
                    this.addSSE(subflow, true);
                });
            },
            delaySSE(generateGraphBeforeDelay, subflow) {
                if(generateGraphBeforeDelay) {
                    this.loadGraph(true);
                }
                setTimeout(() => this.addSSE(subflow), 500);
            },
            addSSE(subflow, generateGraphOnWaiting) {
                let parentExecution = this.execution;

                const parentSubflows = this.expandedSubflows.filter(expandedSubflow => subflow.includes(expandedSubflow + "."))
                    .sort((s1, s2) => s2.length - s1.length);

                if(parentSubflows.length > 0) {
                    parentExecution = this.subflowsExecutions[parentSubflows[0]];
                }

                if(!parentExecution) {
                    this.delaySSE(generateGraphOnWaiting, subflow);
                    return;
                }

                const taskIdMatchingTaskrun = parentExecution.taskRunList
                    .filter(taskRun => taskRun.taskId === Utils.afterLastDot(subflow))?.[0];
                const executionId = taskIdMatchingTaskrun?.outputs?.executionId;

                if(!executionId) {
                    if(taskIdMatchingTaskrun?.state?.current === STATE.SUCCESS) {
                        // Generating more than 1 subflow execution, we're not showing anything
                        this.loadGraph(true);
                        return;
                    }

                    this.delaySSE(generateGraphOnWaiting, subflow);
                    return;
                }

                this.$store.dispatch("execution/followExecution", {id: executionId})
                    .then(sse => {
                        this.sseBySubflow[subflow] = sse;
                        sse.onmessage = (event) => {
                            if (event && event.lastEventId === "end") {
                                sse.close();
                            }

                            const previousExecution = this.subflowsExecutions[subflow];
                            this.$store.commit("execution/addSubflowExecution", {subflow, execution: JSON.parse(event.data)});

                            // add subflow execution id to graph
                            if(previousExecution === undefined) {
                                this.loadGraph(true);
                            }
                        };
                    });
            }
        }
    };
</script>
<style scoped lang="scss">
    .el-card {
        height: calc(100vh - 174px);
        position: relative;

        :deep(.el-card__body) {
            height: 100%;
            display: flex;
        }
    }

    .vueflow {
        height: 100%;
        width: 100%;
    }
</style>

<template>
    <top-nav-bar :title="routeInfo?.title" :breadcrumb="routeInfo?.breadcrumb">
        <template #additional-right v-if="canDelete || isAllowedTrigger || isAllowedEdit">
            <ul>
                <li v-if="isAllowedEdit">
                    <a :href="`${finalApiUrl}/executions/${execution.id}`" target="_blank">
                        <el-button :icon="Api">
                            {{ $t('api') }}
                        </el-button>
                    </a>
                </li>
                <li v-if="canDelete">
                    <el-button :icon="Delete" @click="deleteExecution">
                        {{ $t('delete') }}
                    </el-button>
                </li>
                <li v-if="isAllowedEdit">
                    <el-button :icon="Pencil" @click="editFlow">
                        {{ $t("edit flow") }}
                    </el-button>
                </li>
                <li v-if="isAllowedTrigger">
                    <trigger-flow type="primary" :flow-id="$route.params.flowId" :namespace="$route.params.namespace" />
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <div class="mt-3">
        <div v-if="ready">
            <tabs :route-name="$route.params && $route.params.id ? 'executions/update': ''" @follow="follow" :tabs="tabs" />
        </div>
    </div>
</template>

<script setup>
    import Api from "vue-material-design-icons/Api.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
</script>

<script>
    import Gantt from "./Gantt.vue";
    import Overview from "./Overview.vue";
    import Logs from "./Logs.vue";
    import Topology from "./Topology.vue";
    import ExecutionOutput from "./ExecutionOutput.vue";
    import TriggerFlow from "../flows/TriggerFlow.vue";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Tabs from "../../components/Tabs.vue";

    import State from "../../utils/state";
    import ExecutionMetric from "./ExecutionMetric.vue";
    import {apiUrl} from "override/utils/route"

    export default {
        mixins: [RouteContext],
        components: {
            TriggerFlow,
            Tabs,
            TopNavBar
        },
        data() {
            return {
                sse: undefined,
                previousExecutionId: undefined
            };
        },
        created() {
            this.follow();
            window.addEventListener("popstate", this.follow)
        },
        mounted () {
            this.previousExecutionId = this.$route.params.id
        },
        watch: {
            $route(newValue, oldValue) {
                this.$store.commit("execution/setTaskRun", undefined);
                if (oldValue.name === newValue.name && this.previousExecutionId !== this.$route.params.id) {
                    this.follow()
                }
            },
        },
        methods: {
            follow() {
                const self = this;
                this.closeSSE();
                this.previousExecutionId = this.$route.params.id;
                this.$store
                    .dispatch("execution/followExecution", this.$route.params)
                    .then(sse => {
                        this.sse = sse;
                        this.sse.onmessage = (event) => {
                            if (event && event.lastEventId === "end") {
                                self.closeSSE();
                            }

                            let execution = JSON.parse(event.data);

                            if (!this.flow ||
                                execution.flowId !== this.flow.id ||
                                execution.namespace !== this.flow.namespace ||
                                execution.flowRevision !== this.flow.revision
                            ) {
                                this.$store.dispatch(
                                    "flow/loadFlow",
                                    {namespace: execution.namespace, id: execution.flowId, revision: execution.flowRevision}
                                );
                                this.$store.dispatch("flow/loadRevisions", {
                                    namespace: execution.namespace,
                                    id: execution.flowId
                                })
                            }

                            this.$store.commit("execution/setExecution", execution);
                        }
                    });
            },
            closeSSE() {
                if (this.sse) {
                    this.sse.close();
                    this.sse = undefined;
                }
            },
            getTabs() {
                const title = title => this.$t(title);
                return [
                    {
                        name: undefined,
                        component: Overview,
                        title: title("overview"),
                    },
                    {
                        name: "gantt",
                        component: Gantt,
                        title: title("gantt")
                    },
                    {
                        name: "logs",
                        component: Logs,
                        title: title("logs")
                    },
                    {
                        name: "topology",
                        component: Topology,
                        title: title("topology"),
                    },
                    {
                        name: "outputs",
                        component: ExecutionOutput,
                        title: title("outputs")
                    },
                    {
                        name: "metrics",
                        component: ExecutionMetric,
                        title: title("metrics")
                    }
                ];
            },
            editFlow() {
                this.$router.push({name:"flows/update", params: {
                    namespace: this.$route.params.namespace,
                    id: this.$route.params.flowId,
                    tab: "editor"
                }})
            },
            deleteExecution() {
                if (this.execution) {
                    const item = this.execution;

                    let message = this.$t("delete confirm", {name: item.id});
                    if (State.isRunning(this.execution.state.current)) {
                        message += this.$t("delete execution running");
                    }

                    this.$toast()
                        .confirm(message, () => {
                            return this.$store
                                .dispatch("execution/deleteExecution", item)
                                .then(() => {
                                    return this.$router.push({
                                        name: "executions/list"
                                    });
                                })
                                .then(() => {
                                    this.$toast().deleted(item.id);
                                })
                        });
                }
            },
        },
        computed: {
            ...mapState("flow", ["flow", "revisions"]),
            ...mapState("execution", ["execution"]),
            ...mapState("auth", ["user"]),
            finalApiUrl() {
                return apiUrl(this.$store);
            },
            tabs() {
                return this.getTabs();
            },
            routeInfo() {
                const ns = this.$route.params.namespace;
                const flowId = this.$route.params.flowId;

                if (!ns || !flowId) {
                    return {};
                }

                return {
                    title: this.$route.params.id,
                    breadcrumb: [
                        {
                            label: this.$t("flows"),
                            link: {
                                name: "flows/list",
                                query: {
                                    namespace: ns
                                }
                            }
                        },
                        {
                            label: `${ns}.${flowId}`,
                            link: {
                                name: "flows/update",
                                params: {
                                    namespace: ns,
                                    id: flowId
                                }
                            }
                        },
                        {
                            label: this.$t("executions"),
                            link: {
                                name: "flows/update",
                                params: {
                                    namespace: ns,
                                    id: flowId,
                                    tab: "executions"
                                }
                            }
                        }
                    ]
                };
            },
            isAllowedTrigger() {
                return this.user && this.execution && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.execution.namespace);
            },
            isAllowedEdit() {
                return this.user && this.execution && this.user.isAllowed(permission.FLOW, action.UPDATE, this.execution.namespace);
            },
            canDelete() {
                return this.user && this.execution && this.user.isAllowed(permission.EXECUTION, action.DELETE, this.execution.namespace);
            },
            ready() {
                return this.execution !== undefined;
            }
        },
        beforeUnmount() {
            this.closeSSE();
            window.removeEventListener("popstate", this.follow)
            this.$store.commit("execution/setExecution", undefined);
            this.$store.commit("flow/setFlow", undefined);
            this.$store.commit("flow/setFlowGraph", undefined);
        }
    };
</script>

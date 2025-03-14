<template>
    <top-nav-bar :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li>
                    <div class="el-input el-input-file custom-upload">
                        <div class="el-input__wrapper">
                            <label for="importFlows">
                                <Upload />
                                {{ $t('import') }}
                            </label>
                            <input
                                id="importFlows"
                                class="el-input__inner"
                                type="file"
                                @change="importFlows()"
                                ref="file"
                            >
                        </div>
                    </div>
                </li>
                <li>
                    <router-link :to="{name: 'flows/search'}">
                        <el-button :icon="TextBoxSearch">
                            {{ $t('source search') }}
                        </el-button>
                    </router-link>
                </li>
                <li>
                    <router-link :to="{name: 'flows/create'}">
                        <el-button :icon="Plus" type="primary">
                            {{ $t('create') }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <div class="mt-3" v-if="ready">
        <div>
            <data-table
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="total"
            >
                <template #navbar>
                    <el-form-item>
                        <search-field />
                    </el-form-item>
                    <el-form-item>
                        <namespace-select
                            data-type="flow"
                            :value="$route.query.namespace"
                            @update:model-value="onDataTableValue('namespace', $event)"
                        />
                    </el-form-item>
                    <el-form-item>
                        <label-filter
                            :model-value="$route.query.labels"
                            @update:model-value="onDataTableValue('labels', $event)"
                        />
                    </el-form-item>
                </template>

                <template #top>
                    <state-global-chart
                        class="mb-4"
                        v-if="daily"
                        :ready="dailyReady"
                        :data="daily"
                    />
                </template>

                <template #table>
                    <select-table
                        ref="selectTable"
                        :data="flows"
                        :default-sort="{prop: 'id', order: 'ascending'}"
                        stripe
                        table-layout="auto"
                        fixed
                        @row-dblclick="onRowDoubleClick"
                        @sort-change="onSort"
                        :row-class-name="rowClasses"
                        @selection-change="handleSelectionChange"
                        :selectable="canCheck"
                    >
                        <template #select-actions>
                            <bulk-select
                                :select-all="queryBulkAction"
                                :selections="selection"
                                :total="total"
                                @update:select-all="toggleAllSelection"
                                @unselect="toggleAllUnselected"
                            >
                                <el-button v-if="canRead" :icon="Download" @click="exportFlows()">
                                    {{ $t('export') }}
                                </el-button>
                                <el-button v-if="canDelete" @click="deleteFlows" :icon="TrashCan">
                                    {{ $t('delete') }}
                                </el-button>
                                <el-button v-if="canUpdate" @click="enableFlows" :icon="FileDocumentCheckOutline">
                                    {{ $t('enable') }}
                                </el-button>
                                <el-button v-if="canUpdate" @click="disableFlows" :icon="FileDocumentRemoveOutline">
                                    {{ $t('disable') }}
                                </el-button>
                            </bulk-select>
                        </template>
                        <template #default>
                            <el-table-column prop="id" sortable="custom" :sort-orders="['ascending', 'descending']"
                                             :label="$t('id')">
                                <template #default="scope">
                                    <router-link
                                        :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.id}}"
                                    >
                                        {{ $filters.invisibleSpace(scope.row.id) }}
                                    </router-link>
                                    &nbsp;<markdown-tooltip
                                    :id="scope.row.namespace + '-' + scope.row.id"
                                    :description="scope.row.description"
                                    :title="scope.row.namespace + '.' + scope.row.id"
                                />
                                </template>
                            </el-table-column>

                            <el-table-column :label="$t('labels')">
                                <template #default="scope">
                                    <labels :labels="scope.row.labels" />
                                </template>
                            </el-table-column>

                            <el-table-column prop="namespace" sortable="custom"
                                             :sort-orders="['ascending', 'descending']"
                                             :label="$t('namespace')"
                                             :formatter="(_, __, cellValue) => $filters.invisibleSpace(cellValue)" />

                            <el-table-column prop="state.startDate"
                                            :label="$t('last execution date')"
                                            v-if="user.hasAny(permission.EXECUTION)">
                                <template #default="scope">
                                    <date-ago v-if="lastExecutionByFlowReady" :inverted="true" :date=getLastExecution(scope.row).startDate />
                                </template>
                            </el-table-column>

                            <el-table-column prop="state.current"
                                            :label="$t('last execution status')"
                                            v-if="user.hasAny(permission.EXECUTION)">
                                <template #default="scope">
                                    <status v-if="lastExecutionByFlowReady && getLastExecution(scope.row).lastStatus" :status=getLastExecution(scope.row).lastStatus size="small" />
                                </template>
                            </el-table-column>

                            <el-table-column
                                prop="state"
                                :label="$t('execution statistics')"
                                v-if="user.hasAny(permission.EXECUTION)"
                                class-name="row-graph"
                            >
                                <template #default="scope">
                                    <state-chart
                                        :duration="true"
                                        :namespace="scope.row.namespace"
                                        :flow-id="scope.row.id"
                                        v-if="dailyGroupByFlowReady"
                                        :data="chartData(scope.row)"
                                    />
                                </template>
                            </el-table-column>

                            <el-table-column :label="$t('triggers')" class-name="row-action">
                                <template #default="scope">
                                    <trigger-avatar :flow="scope.row" />
                                </template>
                            </el-table-column>

                            <el-table-column column-key="action" class-name="row-action">
                                <template #default="scope">
                                    <router-link
                                        :to="{name: 'flows/update', params : {namespace: scope.row.namespace, id: scope.row.id}}">
                                        <kicon :tooltip="$t('details')" placement="left">
                                            <TextSearch />
                                        </kicon>
                                    </router-link>
                                </template>
                            </el-table-column>
                        </template>
                    </select-table>
                </template>
            </data-table>
        </div>
    </div>
</template>

<script setup>
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import FileDocumentRemoveOutline from "vue-material-design-icons/FileDocumentRemoveOutline.vue";
    import FileDocumentCheckOutline from "vue-material-design-icons/FileDocumentCheckOutline.vue";
</script>

<script>
    import {mapState} from "vuex";
    import _merge from "lodash/merge";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import DateAgo from "../layout/DateAgo.vue";
    import SelectTableActions from "../../mixins/selectTableActions";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTable from "../layout/DataTable.vue";
    import SearchField from "../layout/SearchField.vue";
    import StateChart from "../stats/StateChart.vue";
    import StateGlobalChart from "../stats/StateGlobalChart.vue";
    import Status from "../Status.vue";
    import TriggerAvatar from "./TriggerAvatar.vue";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue"
    import Kicon from "../Kicon.vue"
    import Labels from "../layout/Labels.vue"
    import Upload from "vue-material-design-icons/Upload.vue";
    import LabelFilter from "../labels/LabelFilter.vue";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions, SelectTableActions],
        components: {
            NamespaceSelect,
            TextSearch,
            DataTable,
            DateAgo,
            SearchField,
            StateChart,
            StateGlobalChart,
            Status,
            TriggerAvatar,
            MarkdownTooltip,
            Kicon,
            Labels,
            Upload,
            LabelFilter,
            TopNavBar
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                permission: permission,
                action: action,
                dailyGroupByFlowReady: false,
                lastExecutionByFlowReady: false,
                dailyReady: false,
                file: undefined,
            };
        },
        computed: {
            ...mapState("flow", ["flows", "total"]),
            ...mapState("stat", ["dailyGroupByFlow", "daily", "lastExecutions"]),
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$t("flows")
                };
            },
            endDate() {
                return new Date();
            },
            startDate() {
                return this.$moment(this.endDate)
                    .add(-30, "days")
                    .toDate();
            },
            canCheck() {
                return this.canRead || this.canDelete || this.canUpdate;
            },
            canRead() {
                return this.user && this.user.isAllowed(permission.FLOW, action.READ, this.$route.query.namespace);
            },
            canDelete() {
                return this.user && this.user.isAllowed(permission.FLOW, action.DELETE, this.$route.query.namespace);
            },
            canUpdate() {
                return this.user && this.user.isAllowed(permission.FLOW, action.UPDATE, this.$route.query.namespace);
            }
        },
        methods: {
            selectionMapper(element) {
                return {
                    id: element.id,
                    namespace: element.namespace
                }
            },
            exportFlows() {
                this.$toast().confirm(
                    this.$t("flow export", {"flowCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("flow/exportFlowByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(_ => {
                                    this.$toast().success(this.$t("flows exported"));
                                })
                        } else {
                            return this.$store
                                .dispatch("flow/exportFlowByIds", {ids: this.selection})
                                .then(_ => {
                                    this.$toast().success(this.$t("flows exported"));
                                })
                        }
                    },
                    () => {
                    }
                )
            },
            disableFlows() {
                this.$toast().confirm(
                    this.$t("flow disable", {"flowCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("flow/disableFlowByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("flows disabled", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        } else {
                            return this.$store
                                .dispatch("flow/disableFlowByIds", {ids: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("flows disabled", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        }
                    },
                    () => {
                    }
                )
            },
            enableFlows() {
                this.$toast().confirm(
                    this.$t("flow enable", {"flowCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("flow/enableFlowByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("flows enabled", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        } else {
                            return this.$store
                                .dispatch("flow/enableFlowByIds", {ids: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("flows enabled", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        }
                    },
                    () => {
                    }
                )
            },
            deleteFlows() {
                this.$toast().confirm(
                    this.$t("flow delete", {"flowCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("flow/deleteFlowByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("flows deleted", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        } else {
                            return this.$store
                                .dispatch("flow/deleteFlowByIds", {ids: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("flows deleted", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        }
                    },
                    () => {
                    }
                )
            },
            importFlows() {
                const formData = new FormData();
                formData.append("fileUpload", this.$refs.file.files[0]);
                this.$store
                    .dispatch("flow/importFlows", formData)
                    .then(_ => {
                        this.$toast().success(this.$t("flows imported"));
                        this.loadData(() => {
                        })
                    })
            },
            chartData(row) {
                if (this.dailyGroupByFlow && this.dailyGroupByFlow[row.namespace] && this.dailyGroupByFlow[row.namespace][row.id]) {
                    return this.dailyGroupByFlow[row.namespace][row.id];
                } else {
                    return [];
                }
            },
            getLastExecution(row) {
                let noState = { state: null, startDate: null }
                if (this.lastExecutions && this.lastExecutions.length > 0) {
                    let filteredFlowExec = this.lastExecutions.filter((executedFlow) => executedFlow.flowId == row.id && executedFlow.namespace == row.namespace)
                    if (filteredFlowExec.length > 0) {
                        return {
                            lastStatus: filteredFlowExec[0].state?.current,
                            startDate: filteredFlowExec[0].state?.startDate
                        }
                    }
                    return noState
                }
                else {
                    return noState
                }
            },
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                return _merge(base, queryFilter)
            },
            loadData(callback) {
                this.dailyReady = false;

                if (this.user.hasAny(permission.EXECUTION)) {
                    this.$store
                        .dispatch("stat/daily", this.loadQuery({
                            startDate: this.$moment(this.startDate).add(-1, "day").startOf("day").toISOString(true),
                            endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
                        }))
                        .then(() => {
                            this.dailyReady = true;
                        });
                }

                this.$store
                    .dispatch("flow/findFlows", this.loadQuery({
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        sort: this.$route.query.sort || "id:asc"
                    }))
                    .then(flows => {
                        this.dailyGroupByFlowReady = false;
                        this.lastExecutionByFlowReady = false;

                        if (flows.results && flows.results.length > 0) {
                            if (this.user && this.user.hasAny(permission.EXECUTION)) {
                                this.$store
                                    .dispatch("stat/dailyGroupByFlow", {
                                        flows: flows.results
                                            .map(flow => {
                                                return {namespace: flow.namespace, id: flow.id}
                                            }),
                                        startDate: this.$moment(this.startDate).add(-1, "day").startOf("day").toISOString(true),
                                        endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
                                    })
                                    .then(() => {
                                        this.dailyGroupByFlowReady = true
                                    })

                                this.$store
                                    .dispatch("stat/lastExecutions", {
                                        flows: flows.results
                                            .map(flow => {
                                                return {namespace: flow.namespace, id: flow.id}
                                            }),
                                    })
                                    .then(() => {
                                        this.lastExecutionByFlowReady = true
                                    })
                            }
                        }
                    })
                    .finally(callback);
            },
            rowClasses(row) {
                return row && row.row && row.row.disabled ? "disabled" : "";
            }
        }
    };
</script>

<style lang="scss" scoped>
    :deep(nav .dropdown-menu) {
        display: flex;
        width: 20rem;
    }
</style>
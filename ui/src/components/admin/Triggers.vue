<template>
    <top-nav-bar :title="routeInfo.title" />
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
                        <refresh-button class="float-right" @refresh="load(onDataLoaded)" />
                    </el-form-item>
                </template>
                <template #table>

                    <el-table
                        :data="triggers"
                        ref="table"
                        :default-sort="{prop: 'flowId', order: 'ascending'}"
                        stripe
                        table-layout="auto"
                        fixed
                        @sort-change="onSort"
                    >
                        <el-table-column prop="triggerId" sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('id')" />
                        <el-table-column prop="flowId" sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('flow')">
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.flowId}}"
                                >
                                    {{ $filters.invisibleSpace(scope.row.flowId) }}
                                </router-link>
                                <markdown-tooltip
                                    :id="scope.row.namespace + '-' + scope.row.flowId"
                                    :description="scope.row.description"
                                    :title="scope.row.namespace + '.' + scope.row.flowId"
                                />
                            </template>
                        </el-table-column>
                        <el-table-column prop="namespace" sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('namespace')">
                            <template #default="scope">
                                {{ $filters.invisibleSpace(scope.row.namespace) }}
                            </template>
                        </el-table-column>

                        <el-table-column :label="$t('current execution')">
                            <template #default="scope">
                                <router-link
                                    v-if="scope.row.executionId"
                                    :to="{name: 'executions/update', params: {namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.executionId}}"
                                >
                                    {{ scope.row.executionId }}
                                </router-link>
                            </template>
                        </el-table-column>

                        <el-table-column prop="executionCurrentState" :label="$t('state')" />
                        <el-table-column :label="$t('date')">
                            <template #default="scope">
                                {{ scope.row.date ? $filters.date(scope.row.date, "iso") : "" }}
                            </template>
                        </el-table-column>
                        <el-table-column :label="$t('updated date')">
                            <template #default="scope">
                                {{ scope.row.updatedDate ? $filters.date(scope.row.updatedDate, "iso") : "" }}
                            </template>
                        </el-table-column>
                        <el-table-column :label="$t('evaluation lock date')">
                            <template #default="scope">
                                {{ scope.row.evaluateRunningDate ? $filters.date(scope.row.evaluateRunningDate, "iso") : "" }}
                            </template>
                        </el-table-column>
                        <el-table-column v-if="user.hasAnyAction(permission.FLOW, action.UPDATE)" column-key="action" class-name="row-action">
                            <template #default="scope">
                                <el-button text v-if="scope.row.executionId || scope.row.evaluateRunningDate">
                                    <kicon
                                        :tooltip="$t(`unlock trigger.tooltip.${scope.row.executionId ? 'execution' : 'evaluation'}`)"
                                        placement="left" @click="triggerToUnlock = scope.row">
                                        <lock-off />
                                    </kicon>
                                </el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </template>
            </data-table>

            <el-dialog v-model="triggerToUnlock" destroy-on-close :append-to-body="true">
                <template #header>
                    <span v-html="$t('unlock trigger.confirmation')" />
                </template>
                {{ $t("unlock trigger.warning") }}
                <template #footer>
                    <el-button :icon="LockOff" @click="unlock" type="primary">
                        {{ $t('unlock trigger.button') }}
                    </el-button>
                </template>
            </el-dialog>
        </div>
    </div>
</template>
<script setup>
    import LockOff from "vue-material-design-icons/LockOff.vue";
    import Kicon from "../Kicon.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import TopNavBar from "../layout/TopNavBar.vue";
</script>
<script>
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import SearchField from "../layout/SearchField.vue";
    import DataTable from "../layout/DataTable.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import RefreshButton from "../layout/RefreshButton.vue";
    import {mapState} from "vuex";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            RefreshButton,
            MarkdownTooltip,
            DataTable,
            SearchField,
            NamespaceSelect
        },
        data() {
            return {
                triggers: undefined,
                total: undefined,
                triggerToUnlock: undefined
            };
        },
        methods: {
            loadData(callback) {
                this.$store.dispatch("trigger/search", {
                    namespace: this.$route.query.namespace,
                    q: this.$route.query.q,
                    size: parseInt(this.$route.query.size || 25),
                    page: parseInt(this.$route.query.page || 1),
                    sort: this.$route.query.sort || "triggerId:asc"
                }).then(triggersData => {
                    this.triggers = triggersData.results;
                    this.total = triggersData.total;
                    callback();
                });
            },
            async unlock() {
                const namespace = this.triggerToUnlock.namespace;
                const flowId = this.triggerToUnlock.flowId;
                const triggerId = this.triggerToUnlock.triggerId;
                const unlockedTrigger = await this.$store.dispatch("trigger/unlock", {
                    namespace: namespace,
                    flowId: flowId,
                    triggerId: triggerId
                });

                this.$message({
                    message: this.$t("unlock trigger.success"),
                    type: "success"
                });

                const triggerIdx = this.triggers.findIndex(trigger => trigger.namespace === namespace && trigger.flowId === flowId && trigger.triggerId === triggerId);
                if (triggerIdx !== -1) {
                    this.triggers[triggerIdx] = unlockedTrigger;
                }

                this.triggerToUnlock = undefined;
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$t("triggers")
                }
            }
        }
    };
</script>
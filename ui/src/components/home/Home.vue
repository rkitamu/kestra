<template>
    <top-nav-bar v-if="!embed" :title="routeInfo.title">
        <template #additional-right>
            <ul>
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
    <div :class="{'mt-3': !embed}" class="home" v-loading="!dailyReady">
        <div v-if="displayCharts">
            <collapse>
                <el-form-item v-if="!flowId && !namespaceRestricted">
                    <namespace-select
                        :data-type="'flow'"
                        :model-value="selectedNamespace"
                        @update:model-value="onNamespaceSelect"
                    />
                </el-form-item>
                <el-form-item
                    class="date-range"
                >
                    <date-range
                        :start-date="startDate"
                        :end-date="endDate"
                        @update:model-value="onDateChange($event)"
                    />
                </el-form-item>
            </collapse>

            <state-global-chart
                class="big mb-4"
                v-if="dailyReady"
                :ready="dailyReady"
                :data="daily"
                :big="true"
                :start-date="startDate"
                :end-date="endDate"
            />

            <home-description v-if="namespace" :description="description" class="mb-4" />

            <el-row v-if="displayPieChart" :gutter="15" class="auto-height mb-4">
                <el-col :lg="8" class="mb-3 mb-xl-0">
                    <home-summary-pie
                        v-if="dailyReady"
                        :title="todayTitle"
                        :data="today"
                    />
                </el-col>
                <el-col :lg="8" class="mb-3 mb-xl-0">
                    <home-summary-pie
                        v-if="dailyReady"
                        :title="yesterdayTitle"
                        :data="yesterday"
                    />
                </el-col>
                <el-col :lg="8" class="mb-3 mb-xl-0">
                    <home-summary-pie
                        v-if="dailyReady"
                        :title="lastDaysTitle"
                        :data="alls"
                    />
                </el-col>
            </el-row>

            <el-row :gutter="15" v-if="!namespace" class="auto-height mb-4">
                <el-col :lg="12">
                    <home-summary-namespace
                        v-if="dailyGroupByFlowReady"
                        :data="namespacesStats"
                    />
                </el-col>
                <el-col :lg="12">
                    <home-summary-namespace
                        v-if="dailyGroupByFlowReady"
                        :failed="true"
                        :data="namespacesStats"
                    />
                </el-col>
            </el-row>

            <el-row :gutter="15">
                <el-col :lg="12">
                    <home-summary-failed
                        v-if="dailyReady"
                        :filters="defaultFilters"
                        class="mb-4"
                    />
                </el-col>
                <el-col :lg="12">
                    <home-summary-log
                        v-if="dailyReady"
                        :filters="defaultFilters"
                        class="mb-4"
                    />
                </el-col>
            </el-row>
        </div>
        <div v-else-if="dailyReady">
            <el-card class="pb-3">
                <el-row justify="center">
                    <span class="new-execution-img" />
                </el-row>
                <el-row justify="center" class="mb-4">
                    <el-col>
                        <h3 class="text-center">
                            {{ $t('homeDashboard.no executions') }}
                        </h3>
                    </el-col>
                </el-row>
                <el-row justify="center">
                    <trigger-flow v-if="flowId" :disabled="!isAllowedTrigger" :flow-id="flowId" :namespace="namespace" />
                    <router-link v-else :to="{name: 'flows/list'}">
                        <el-button size="large" type="primary">
                            {{ $t('execute') }}
                        </el-button>
                    </router-link>
                </el-row>
            </el-card>
            <onboarding-bottom v-if="!flowId" />
        </div>
    </div>
</template>

<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
</script>

<script>
    import Collapse from "../layout/Collapse.vue";
    import RouteContext from "../../mixins/routeContext";
    import StateGlobalChart from "../stats/StateGlobalChart.vue";
    import {mapState} from "vuex";
    import _cloneDeep from "lodash/cloneDeep"
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import HomeSummaryPie from "./HomeSummaryPie.vue";
    import HomeSummaryFailed from "./HomeSummaryFailed.vue";
    import HomeSummaryLog from "./HomeSummaryLog.vue";
    import RestoreUrl from "../../mixins/restoreUrl";
    import HomeSummaryNamespace from "./HomeSummaryNamespace.vue";
    import HomeDescription from "./HomeDescription.vue";
    import _merge from "lodash/merge";
    import TriggerFlow from "../flows/TriggerFlow.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import OnboardingBottom from "../onboarding/OnboardingBottom.vue";
    import DateRange from "../layout/DateRange.vue";
    import TopNavBar from "../layout/TopNavBar.vue";

    export default {
        mixins: [RouteContext, RestoreUrl],
        components: {
            DateRange,
            OnboardingBottom,
            Collapse,
            StateGlobalChart,
            NamespaceSelect,
            HomeSummaryPie,
            HomeSummaryFailed,
            HomeSummaryLog,
            HomeSummaryNamespace,
            HomeDescription,
            TriggerFlow,
            TopNavBar
        },
        props: {
            namespace: {
                type: String,
                default: undefined
            },
            flowId: {
                type: String,
                default: undefined
            },
            description: {
                type: String,
                default: undefined
            }
        },
        created() {
            this.loadStats();
            this.haveExecutions();
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name && newValue.query !== oldValue.query) {
                    this.loadStats();
                }
            },
            flowId() {
                this.loadStats();
                this.haveExecutions();
            }
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                dailyReady: false,
                dailyGroupByFlowReady: false,
                today: undefined,
                yesterday: undefined,
                executionCounts: undefined,
                alls: undefined,
                namespacesStats: undefined,
                namespaceRestricted: !!this.namespace
            };
        },
        methods: {
            loadQuery(base, stats) {
                let queryFilter = _cloneDeep(this.$route.query);

                if (stats) {
                    delete queryFilter["startDate"];
                    delete queryFilter["endDate"];
                }

                if (this.selectedNamespace) {
                    queryFilter["namespace"] = this.selectedNamespace;
                }

                if (this.flowId) {
                    queryFilter["flowId"] = this.flowId;
                }

                return _merge(base, queryFilter)
            },
            haveExecutions() {
                let params = {
                    size: 1
                };
                if (this.selectedNamespace) {
                    params["namespace"] = this.selectedNamespace;
                }

                if (this.flowId) {
                    params["flowId"] = this.flowId;
                }
                this.$store.dispatch("execution/findExecutions", params )
                    .then(executions => {
                        this.executionCounts = executions.total;
                    });
            },
            loadStats() {
                this.dailyReady = false;
                this.$store
                    .dispatch("stat/daily", this.loadQuery({
                        startDate: this.$moment(this.startDate).toISOString(true),
                        endDate: this.$moment(this.endDate).toISOString(true)
                    }, true))
                    .then((daily) => {
                        let data = [...daily];
                        let sorted = data.sort((a, b) => {
                            return new Date(b.date) - new Date(a.date);
                        });
                        this.today = sorted.at(sorted.length - 1);
                        this.yesterday = sorted.length >= 2 ? sorted.at(sorted.length - 2) : {};
                        this.alls = this.mergeStats(sorted);
                        this.dailyReady = true;
                    });

                if (!this.flowId) {
                    this.dailyGroupByFlowReady = false;
                    this.$store
                        .dispatch("stat/dailyGroupByFlow", this.loadQuery({
                            startDate: this.$moment(this.startDate).toISOString(true),
                            endDate: this.$moment(this.endDate).toISOString(true),
                            namespaceOnly: true
                        }))
                        .then((daily) => {
                            this.namespacesStats = daily;
                            this.dailyGroupByFlowReady = true;
                        });
                }
            },
            mergeStats(daily) {
                return daily
                    .reduce((accumulator, value)  => {
                        if (!accumulator) {
                            accumulator = _cloneDeep(value);
                        } else {
                            this.sumAll(value.executionCounts, accumulator.executionCounts);
                            this.sumAll(value.duration, accumulator.duration);
                        }
                        return accumulator;
                    }, null);
            },
            sumAll(object, accumulator) {
                for (const key in object) {
                    accumulator[key] += object[key]
                }
            },
            onNamespaceSelect(namespace) {
                if(namespace !== ""){
                    this.$router.push({
                        query: {...this.$route.query, namespace}
                    });
                } else {
                    let query = _cloneDeep(this.$route.query);
                    delete query["namespace"];
                    this.$router.push({
                        query: {...query}
                    });
                }
            },
            onDateChange(dates) {
                if(dates.startDate && dates.endDate) {
                    this.$router.push({
                        query: {...this.$route.query, ...{startDate: dates.startDate, endDate: dates.endDate}}
                    });
                } else {
                    let query = _cloneDeep(this.$route.query);
                    delete query["startDate"];
                    delete query["endDate"];
                    this.$router.push({
                        query: {...query}
                    });
                }
            }
        },
        computed: {
            ...mapState("stat", ["daily", "dailyGroupByFlow"]),
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$t("homeDashboard.title"),
                };
            },
            defaultFilters() {
                return {
                    startDate: this.$moment(this.startDate).toISOString(true),
                    endDate: this.$moment(this.endDate).toISOString(true)
                }
            },
            displayCharts() {
                if (this.executionCounts > 0) {
                    return true
                }
                // not flow home
                if (!this.flowId) {
                    if (!this.$route.query.namespace && this.executionCounts === 0) {
                        return false;
                    }
                }
                if (this.executionCounts === 0) {
                    return false;
                }

                return true;
            },
            selectedNamespace() {
                return this.namespace || this.$route.query.namespace;
            },
            endDate() {
                return this.$route.query.endDate ? this.$route.query.endDate : this.$moment().toISOString(true);
            },
            startDate() {
                return this.$route.query.startDate ? this.$route.query.startDate : this.$moment(this.endDate)
                    .add(-30, "days").toISOString(true);
            },
            isAllowedTrigger() {
                return this.user && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.namespace);
            },
            todayTitle() {
                return this.$t("homeDashboard.today");
            },
            yesterdayTitle() {
                return this.$t("homeDashboard.yesterday");
            },
            lastDaysTitle() {
                return this.$t("homeDashboard.lastXdays",
                               {days: this.$moment(this.yesterday.date)
                                   .diff(this.$moment(this.startDate), "days")});
            },
            displayPieChart() {
                return this.$moment(this.endDate).isSame(this.$moment(), "day")
                    && this.$moment(this.today?.date).isSame(this.$moment(), "day")
                    && this.$moment(this.endDate).diff(this.$moment(this.startDate), "days") >= 3;
            }
        }
    };
</script>

<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/variables";

    .home {
        .auto-height {
            .el-card {
                height: 100%;
            }
        }

        .new-execution-img {
            height: 300px;
            width: 100%;
            background: url("../../assets/home/execute-light.svg") no-repeat center;

            html.dark & {
                background: url("../../assets/home/execute-dark.svg") no-repeat center;
            }
        }
    }
</style>

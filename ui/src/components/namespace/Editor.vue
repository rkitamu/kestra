<template>
    <top-nav-bar :title="routeInfo.title">
        <template #additional-right>
            <namespace-select
                class="fit-content"
                data-type="flow"
                :value="namespace"
                @update:model-value="namespaceUpdate"
                allow-create
                :is-filter="false"
            />
            <trigger-flow
                :disabled="!flow"
                :flow-id="flow"
                :namespace="namespace"
            />
        </template>
    </top-nav-bar>
    <iframe
        style="visibility:hidden;"
        onload="this.style.visibility = 'visible';"
        v-if="namespace"
        class="vscode-editor"
        :src="vscodeIndexUrl"
        ref="vscodeIde"
    />
    <div v-else class="m-3 mw-100">
        <el-alert type="info" :closable="false">
            {{ $t("namespace choice") }}
        </el-alert>
    </div>
</template>

<script setup>
    import NamespaceSelect from "./NamespaceSelect.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import TriggerFlow from "../flows/TriggerFlow.vue";
</script>

<script>
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import {apiUrl} from "override/utils/route";
    import {mapState} from "vuex";
    import {storageKeys} from "../../utils/constants";

    export default {
        mixins: [RouteContext, RestoreUrl],
        methods: {
            namespaceUpdate(namespace) {
                localStorage.setItem(storageKeys.LATEST_NAMESPACE, namespace);
                this.$router.push({
                    params: {
                        namespace
                    }
                });
            },
            handleTabsDirty(tabs) {
                // Add tabs not saved
                this.tabsNotSaved = this.tabsNotSaved.concat(tabs.dirty)
                // Removed tabs closed
                this.tabsNotSaved = this.tabsNotSaved.filter(e => !tabs.closed.includes(e))
                this.$store.dispatch("core/isUnsaved", this.tabsNotSaved.length > 0);
            }
        },
        data() {
            return {
                flow: null,
                tabsNotSaved: []
            }
        },
        mounted() {
            window.addEventListener("message", (event) => {
                const message = event.data;
                if (message.type === "kestra.tabFileChanged") {
                    const flowsFolderPath = `/${this.namespace}/_flows/`;
                    const filePath = message.filePath.path;
                    if (filePath.startsWith(flowsFolderPath)) {
                        const fileName = filePath.split(flowsFolderPath)[1];
                        // trim the eventual extension
                        this.flow = fileName.split(".")[0];
                    } else {
                        this.flow = null;
                    }
                } else if (message.type === "kestra.tabsChanged") {
                    this.handleTabsDirty(message.tabs);
                }
            });

            // Setup namespace
            const namespace = localStorage.getItem(storageKeys.LATEST_NAMESPACE) ? localStorage.getItem(storageKeys.LATEST_NAMESPACE) : localStorage.getItem(storageKeys.DEFAULT_NAMESPACE);
            if (namespace) {
                this.namespaceUpdate(namespace);
            } else if (this.namespaces?.length > 0) {
                this.namespaceUpdate(this.namespaces[0]);
            } else if (localStorage.getItem("tourDoneOrSkip") !== "true") {
                this.$router.push({name: "flows/create"});
            }
        },
        computed: {
            ...mapState("namespace", ["namespaces"]),
            routeInfo() {
                return {
                    title: this.$t("editor")
                };
            },
            theme() {
                return localStorage.getItem("theme") || "light";
            },
            vscodeIndexUrl() {
                const uiSubpath = KESTRA_UI_PATH === "./" ? "/" : KESTRA_UI_PATH;
                return `${uiSubpath}vscode.html?KESTRA_UI_PATH=${uiSubpath}&KESTRA_API_URL=${apiUrl(this.$store)}&THEME=${this.theme}&namespace=${this.namespace}`;
            },
            namespace() {
                return this.$route.params.namespace;
            }
        }
    }
</script>

<style lang="scss">
    .fit-content {
        width: fit-content;
    }
    .vscode-editor {
        height: calc(100vh - 64.8px);
        width: 100%;
    }
</style>
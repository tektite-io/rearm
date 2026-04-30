<template>
    <h3 class="release-header">
        <template v-if="componentName && componentUuid && orgUuid">
            <router-link :to="componentLink">{{ componentName }}</router-link>
            <span v-if="branchName"> / </span>
        </template>
        <router-link v-if="branchName && branchUuid && orgUuid" :to="branchLink">{{ branchName }}</router-link>
        <span v-if="(componentName && componentUuid && orgUuid) || (branchName && branchUuid && orgUuid)"> &mdash; </span>
        <router-link :to="releaseLink">{{ version }}</router-link>
        <n-tag v-if="lifecycle === 'REJECTED'" type="error" size="small" class="lifecycle-tag">REJECTED</n-tag>
        <n-tag v-else-if="lifecycle === 'PENDING'" type="warning" size="small" class="lifecycle-tag">PENDING</n-tag>
        <n-tag v-if="branchChangeType === 'ADDED'" type="success" size="small" class="lifecycle-tag">New Component</n-tag>
        <n-tag v-else-if="branchChangeType === 'REMOVED'" type="error" size="small" class="lifecycle-tag">Component Removed</n-tag>
    </h3>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { NTag } from 'naive-ui'

interface Props {
    uuid: string
    version: string
    lifecycle?: string
    orgUuid?: string
    componentUuid?: string
    componentName?: string
    branchUuid?: string
    branchName?: string
    branchChangeType?: string
}

const props = defineProps<Props>()

const releaseLink = computed(() => ({
    name: 'ReleaseView',
    params: { uuid: props.uuid }
}))

const componentLink = computed(() => ({
    name: 'ComponentsOfOrg',
    params: { orguuid: props.orgUuid, compuuid: props.componentUuid }
}))

const branchLink = computed(() => ({
    name: 'ComponentsOfOrg',
    params: { orguuid: props.orgUuid, compuuid: props.componentUuid, branchuuid: props.branchUuid }
}))
</script>

<style scoped lang="scss">
.release-header {
    margin-top: 12px;
    margin-bottom: 8px;

    .lifecycle-tag {
        margin-left: 8px;
    }

    a {
        color: #18a058;
        text-decoration: none;

        &:hover {
            text-decoration: underline;
        }
    }
}
</style>

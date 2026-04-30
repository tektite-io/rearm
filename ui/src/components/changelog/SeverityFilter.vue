<template>
    <div class="severity-filter">
        <n-form-item label="Filter By Severity:">
            <n-select 
                :value="selectedSeverity" 
                @update:value="handleUpdate"
                :options="severityOptions" 
            />
        </n-form-item>
    </div>
</template>

<script lang="ts" setup>
import { NFormItem, NSelect } from 'naive-ui'

interface Props {
    selectedSeverity?: string
}

const props = withDefaults(defineProps<Props>(), {
    selectedSeverity: 'ALL'
})

const emit = defineEmits<{
    'update:selectedSeverity': [value: string]
}>()

// Values are raw conventional-commit prefixes emitted by the backend
// (CommitType.getPrefix), so they match changeType on each commit.
const severityOptions = [
    { label: 'ALL', value: 'ALL' },
    { label: 'Bug Fixes', value: 'fix' },
    { label: 'Features', value: 'feat' },
    { label: 'Performance Improvements', value: 'perf' },
    { label: 'Reverts', value: 'revert' },
    { label: 'Code Refactoring', value: 'refactor' },
    { label: 'Builds', value: 'build' },
    { label: 'Tests', value: 'test' },
    { label: 'Documentation', value: 'docs' },
    { label: 'Chores', value: 'chore' },
    { label: 'Continuous Integration', value: 'ci' },
    { label: 'Styles', value: 'style' },
    { label: 'Others', value: 'other' }
]

const handleUpdate = (value: string) => {
    emit('update:selectedSeverity', value)
}
</script>

<style scoped lang="scss">
.severity-filter {
    margin-bottom: 16px;
    max-width: 30%;
}
</style>

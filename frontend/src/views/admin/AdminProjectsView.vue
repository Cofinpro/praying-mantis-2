<script setup lang="ts">
import { computed, ref } from 'vue'
import { Lock, Pencil, Plus, TriangleAlert } from 'lucide-vue-next'

import type { Project } from '@/api/client'
import { isForbidden, problemMessage } from '@/api/problems'
import { useAdminProjects, useUpdateAdminProject } from '@/admin/queries'
import BaseButton from '@/components/BaseButton.vue'
import BaseToggle from '@/components/BaseToggle.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ProjectDialog from '@/components/admin/ProjectDialog.vue'
import { clientLabel } from '@/format/labels'

// FE-9.3, frame "14 Admin – Projects". Projects are never deleted, since time entries point at
// them (decision 35): the Active switch in each row deactivates one, which stops new hours on it.
const projects = useAdminProjects()
const forbidden = computed(() => isForbidden(projects.error.value))

const rows = computed(() =>
  (projects.data.value ?? []).map((p) => ({
    project: p,
    client: p.client ? clientLabel(p.client) : 'Internal',
    editLabel: `Edit ${p.code}`,
    activeLabel: `${p.code} active`,
  })),
)

// The Active switch saves at once: the project as it is, with the flag flipped
const update = useUpdateAdminProject()
const switching = ref<number | null>(null)
const switchError = ref<string | null>(null)

async function setActive(project: Project, isActive: boolean) {
  switching.value = project.id
  switchError.value = null
  const { code, name, client, isBillable } = project
  try {
    await update.mutateAsync({
      id: project.id,
      body: { code, name, isBillable, isActive, ...(client ? { client } : {}) },
    })
  } catch (error) {
    const verb = isActive ? 'activate' : 'deactivate'
    switchError.value = `Couldn’t ${verb} ${code}. ${problemMessage(error)}`
  } finally {
    switching.value = null
  }
}

/** The dialog: `undefined` closed, `null` adding, a project editing */
const editing = ref<Project | null | undefined>(undefined)
</script>

<template>
  <section class="section" aria-labelledby="projects-title">
    <header class="section__header">
      <div>
        <h2 id="projects-title" class="section__title">Projects</h2>
        <p v-if="!forbidden" class="section__subtitle">
          Only active projects can be picked in timesheets
        </p>
      </div>
      <div v-if="!forbidden" class="section__actions">
        <BaseButton :disabled="!projects.isSuccess.value" @click="editing = null">
          <Plus :size="18" aria-hidden="true" />
          Add project
        </BaseButton>
      </div>
    </header>

    <p v-if="projects.isPending.value" class="state" role="status">Loading projects…</p>
    <div v-else-if="forbidden" class="state state--forbidden" role="alert">
      <Lock :size="24" aria-hidden="true" class="state__icon" />
      <p class="state__title">You need an admin account</p>
      <p>Only admins can manage projects. Ask an admin if you need access.</p>
    </div>
    <div v-else-if="projects.isError.value" class="state state--error" role="alert">
      <p>The projects couldn't be loaded.</p>
      <BaseButton variant="secondary" size="small" @click="projects.refetch()"
        >Try again</BaseButton
      >
    </div>
    <p v-else-if="rows.length === 0" class="state" role="status">
      No projects yet. Add the first one.
    </p>
    <template v-else>
      <div v-if="switchError" class="banner" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        {{ switchError }}
      </div>
      <div class="card card--stack">
        <table class="table table--stack">
          <caption class="visually-hidden">
            Projects
          </caption>
          <thead>
            <tr>
              <th scope="col" class="col-code">Code</th>
              <th scope="col">Name</th>
              <th scope="col" class="col-client">Client</th>
              <th scope="col" class="col-billing">Billing</th>
              <th scope="col" class="col-active">Active</th>
              <th scope="col" class="col-edit"><span class="visually-hidden">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.project.id">
              <th scope="row" class="code">{{ row.project.code }}</th>
              <td :class="{ muted: !row.project.isActive }" data-label="Name">
                {{ row.project.name }}
              </td>
              <td :class="{ muted: !row.project.client }" data-label="Client">{{ row.client }}</td>
              <td data-label="Billing">
                <StatusBadge v-if="row.project.isBillable" status="approved" label="Billable" />
                <StatusBadge v-else status="draft" label="Non-billable" />
              </td>
              <td data-label="Active">
                <BaseToggle
                  :model-value="row.project.isActive"
                  :label="row.activeLabel"
                  :disabled="switching === row.project.id"
                  @update:model-value="setActive(row.project, $event)"
                />
              </td>
              <td class="edit stack-corner">
                <button
                  type="button"
                  class="icon-button"
                  :aria-label="row.editLabel"
                  @click="editing = row.project"
                >
                  <Pencil :size="18" aria-hidden="true" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <ProjectDialog
      v-if="editing !== undefined"
      :project="editing ?? undefined"
      @close="editing = undefined"
    />
  </section>
</template>

<style scoped src="../../admin/section.css"></style>

<style scoped>
.col-code {
  width: 170px;
}

.col-client {
  width: 130px;
}

.col-billing {
  width: 150px;
}

.col-active {
  width: 90px;
}

.col-edit {
  width: 64px;
}

.table tbody th.code {
  font-weight: 600;
}

.table td.edit {
  text-align: right;
}

.banner {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) 14px;
  border-radius: var(--radius-input);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-weight: 500;
}
</style>

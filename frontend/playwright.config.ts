import {defineConfig} from '@playwright/test'

export default defineConfig({
  testDir:'./tests',fullyParallel:false,workers:1,timeout:30000,
  use:{baseURL:'http://127.0.0.1:5173',headless:true,channel:process.env.PLAYWRIGHT_CHANNEL || (process.platform==='win32'?'msedge':undefined),trace:'retain-on-failure'},
  webServer:{command:'npm run dev',url:'http://127.0.0.1:5173',reuseExistingServer:!process.env.CI,timeout:60000},
  outputDir:'../output/article-upgrade/test-results',
})

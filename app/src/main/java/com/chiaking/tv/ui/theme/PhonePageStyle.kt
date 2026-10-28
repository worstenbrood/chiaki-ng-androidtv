package com.chiaking.tv.ui.theme

/** Standalone phone-page styling; no remote fonts or assets are loaded. */
internal const val PHONE_SIGN_IN_CSS = """
            *{box-sizing:border-box}body{background:#101729;color:#fff;font:17px system-ui;margin:0;padding:20px}
            main{max-width:600px;margin:20px auto}h1{font-size:26px;margin:0 0 24px}h2{font-size:20px;margin:0}
            p{line-height:1.5;margin:12px 0 18px;color:#d7ddeb}label{display:block;font-size:15px}
            .steps{list-style:none;padding:0;margin:0;counter-reset:step;display:grid;gap:16px}
            .steps>li{counter-increment:step;background:#192238;border:1px solid #303d57;border-radius:16px;padding:20px}
            h2::before{content:counter(step);display:inline-grid;place-items:center;width:30px;height:30px;margin-right:10px;background:#244cb0;border-radius:50%;font-size:17px}
            textarea{width:100%;margin:8px 0 12px;padding:12px;border:1px solid #8993a7;border-radius:8px;font:16px system-ui}
            button,.button{display:inline-block;background:#244cb0;color:#fff;border:0;border-radius:8px;padding:14px 20px;font:inherit;text-decoration:none}
            a{color:#b9d0ff}button,.button{width:100%;text-align:center;cursor:pointer}
            :focus-visible{outline:3px solid #b9d0ff;outline-offset:3px}.notice{font-size:14px;color:#aebad0;margin:20px 4px}
            @media(max-width:420px){body{padding:16px}main{margin:12px auto}.steps>li{padding:16px}}
"""

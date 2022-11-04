### Netflix DGS WebFlux file upload support

This is a workaround for issue https://github.com/Netflix/dgs-framework/issues/819

In order to use this, you need to add all files except `UploadScalar` to your project in some subpackage so they are scanned by Spring.
`UploadScalar` replaces DGS implementation, so its package must remain intact.

Note this is quick-and-dirty hack, use at your own risk.

